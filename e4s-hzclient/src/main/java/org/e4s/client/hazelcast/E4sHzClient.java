package org.e4s.client.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.e4s.client.E4sClient;
import org.e4s.model.GenericBucket;
import org.e4s.model.MeterDayKey;
import org.e4s.model.Timestamped;
import org.e4s.model.dynamic.DynamicModelRegistry;
import org.e4s.model.serialization.GenericBucketHazelcastSerializer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class E4sHzClient implements E4sClient {

    private static final String METER_DATA_MAP = "meter-data";
    private static final String AGGREGATION_FIELD = "power";

    private final HazelcastInstance hazelcastClient;
    private final IMap<String, GenericBucket<Timestamped>> meterDataMap;

    public E4sHzClient(String address) {
        this(createClientConfig(address));
    }

    public E4sHzClient(ClientConfig config) {
        DynamicModelRegistry.getInstance().initialize();
        this.hazelcastClient = HazelcastClient.newHazelcastClient(config);
        this.meterDataMap = hazelcastClient.getMap(METER_DATA_MAP);
    }

    public E4sHzClient(HazelcastInstance hazelcastClient) {
        DynamicModelRegistry.getInstance().initialize();
        this.hazelcastClient = hazelcastClient;
        this.meterDataMap = hazelcastClient.getMap(METER_DATA_MAP);
    }

    @SuppressWarnings("unchecked")
    private static ClientConfig createClientConfig(String address) {
        ClientConfig config = new ClientConfig();
        
        config.getNetworkConfig()
                .addAddress(address.split(","))
                .setSmartRouting(true)
                .setConnectionTimeout(5000);
        
        config.getSerializationConfig().addSerializerConfig(
                new com.hazelcast.config.SerializerConfig()
                        .setTypeClass(GenericBucket.class)
                        .setImplementation(new GenericBucketHazelcastSerializer())
        );
        
        return config;
    }

    @Override
    public void ingestReading(String meterId, Timestamped reading) {
        LocalDate day = Instant.ofEpochMilli(reading.getTimestamp())
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        String key = MeterDayKey.of(meterId, day).toKeyString();

        meterDataMap.compute(key, (k, bucket) -> {
            if (bucket == null) {
                bucket = DynamicModelRegistry.getInstance().createBucket("MeterReading", meterId, day.toEpochDay());
            }
            bucket.addReading(reading);
            return bucket;
        });
    }

    @Override
    public void ingestReadings(String meterId, List<? extends Timestamped> readings) {
        Map<LocalDate, List<Timestamped>> byDay = new HashMap<>();
        for (Timestamped r : readings) {
            LocalDate day = Instant.ofEpochMilli(r.getTimestamp())
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
            byDay.computeIfAbsent(day, d -> new ArrayList<>()).add(r);
        }

        byDay.forEach((day, dayReadings) -> {
            String key = MeterDayKey.of(meterId, day).toKeyString();
            meterDataMap.compute(key, (k, bucket) -> {
                if (bucket == null) {
                    bucket = DynamicModelRegistry.getInstance().createBucket("MeterReading", meterId, day.toEpochDay());
                }
                for (Timestamped reading : dayReadings) {
                    bucket.addReading(reading);
                }
                return bucket;
            });
        });
    }

    @Override
    public void ingestBatch(List<IngestRequest> requests) {
        requests.forEach(req -> ingestReadings(req.getMeterId(), req.getReadings()));
    }

    @Override
    public List<Timestamped> queryRange(String meterId, Instant start, Instant end) {
        List<Timestamped> result = new ArrayList<>();
        LocalDate startDay = start.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDay = end.atZone(ZoneOffset.UTC).toLocalDate();

        LocalDate currentDay = startDay;
        while (!currentDay.isAfter(endDay)) {
            String key = MeterDayKey.of(meterId, currentDay).toKeyString();
            GenericBucket<Timestamped> bucket = meterDataMap.get(key);
            if (bucket != null) {
                result.addAll(bucket.queryRange(start.toEpochMilli(), end.toEpochMilli()));
            }
            currentDay = currentDay.plusDays(1);
        }

        result.sort(Comparator.comparingLong(Timestamped::getTimestamp));
        return result;
    }

    @Override
    public AggregationResult queryAggregation(String meterId, Instant start, Instant end,
                                               AggregationType type, Interval interval) {
        List<Timestamped> readings = queryRange(meterId, start, end);

        AggregationResult result = new AggregationResult();
        result.setMeterId(meterId);
        result.setAggregationType(type);
        result.setInterval(interval);

        if (readings.isEmpty()) {
            return result;
        }

        DynamicModelRegistry registry = DynamicModelRegistry.getInstance();
        
        switch (type) {
            case AVG -> {
                double sum = 0;
                for (Timestamped r : readings) {
                    sum += (Double) registry.getFieldValue(r, AGGREGATION_FIELD);
                }
                result.setValue(sum / readings.size());
                result.setCount(readings.size());
            }
            case SUM -> {
                double sum = 0;
                for (Timestamped r : readings) {
                    sum += (Double) registry.getFieldValue(r, AGGREGATION_FIELD);
                }
                result.setValue(sum);
                result.setCount(readings.size());
            }
            case MIN -> {
                double min = Double.MAX_VALUE;
                for (Timestamped r : readings) {
                    double val = (Double) registry.getFieldValue(r, AGGREGATION_FIELD);
                    if (val < min) {
                        min = val;
                    }
                }
                result.setValue(min);
                result.setCount(readings.size());
            }
            case MAX -> {
                double max = Double.MIN_VALUE;
                for (Timestamped r : readings) {
                    double val = (Double) registry.getFieldValue(r, AGGREGATION_FIELD);
                    if (val > max) {
                        max = val;
                    }
                }
                result.setValue(max);
                result.setCount(readings.size());
            }
            case COUNT -> {
                result.setValue((double) readings.size());
                result.setCount(readings.size());
            }
        }

        return result;
    }

    @Override
    public CacheStats getCacheStats() {
        var stats = meterDataMap.getLocalMapStats();
        CacheStats cacheStats = new CacheStats();
        cacheStats.setTotalEntries(meterDataMap.size());
        cacheStats.setOwnedEntries(stats.getOwnedEntryCount());
        cacheStats.setMemoryBytes(stats.getOwnedEntryMemoryCost());
        cacheStats.setMemoryMB(stats.getOwnedEntryMemoryCost() / (1024.0 * 1024.0));
        cacheStats.setMemoryGB(stats.getOwnedEntryMemoryCost() / (1024.0 * 1024.0 * 1024.0));
        cacheStats.setPutCount(stats.getPutOperationCount());
        cacheStats.setGetCount(stats.getGetOperationCount());
        return cacheStats;
    }

    @Override
    public long getBucketCount() {
        return meterDataMap.size();
    }

    @Override
    public boolean isHealthy() {
        return hazelcastClient.getLifecycleService().isRunning();
    }

    @Override
    public void close() {
        if (hazelcastClient != null) {
            hazelcastClient.shutdown();
        }
    }

    public HazelcastInstance getHazelcastClient() {
        return hazelcastClient;
    }

    public IMap<String, GenericBucket<Timestamped>> getMeterDataMap() {
        return meterDataMap;
    }
}
