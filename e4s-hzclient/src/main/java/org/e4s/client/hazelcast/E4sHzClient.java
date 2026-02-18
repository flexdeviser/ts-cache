package org.e4s.client.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.e4s.client.E4sClient;
import org.e4s.model.MeterBucket;
import org.e4s.model.MeterDayKey;
import org.e4s.model.MeterReading;
import org.e4s.model.serialization.MeterBucketHazelcastSerializer;
import org.e4s.model.serialization.MeterReadingHazelcastSerializer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Native Hazelcast client implementation with client-side serialization.
 * 
 * <p>This client connects directly to the Hazelcast cluster and performs
 * serialization on the client side, providing:
 * <ul>
 *   <li><b>~90% smaller network payload</b> - Kryo + Deflater binary format</li>
 *   <li><b>Zero serialization CPU on server</b> - Server only handles cache operations</li>
 *   <li><b>Higher throughput</b> - Direct IMap access without HTTP overhead</li>
 * </ul>
 * 
 * <h2>Architecture</h2>
 * <pre>
 * ┌──────────────┐     Binary/HZ      ┌──────────────┐
 * │ E4sHzClient  │ ═══════════════►   │  e4s-server  │
 * │              │   Kryo+Deflater    │  (Hazelcast) │
 * │ Serialization│                    │  (cache only)│
 * │ on client    │ ◄═══════════════   │  Eviction    │
 * └──────────────┘    Binary data     └──────────────┘
 * </pre>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * try (E4sHzClient client = new E4sHzClient("localhost:5701")) {
 *     // Ingest data
 *     MeterReading reading = new MeterReading(
 *         Instant.now().toEpochMilli(), 220.5, 5.2, 1146.6);
 *     client.ingestReading("MTR-001", reading);
 *     
 *     // Query data
 *     List<MeterReading> data = client.queryRange(
 *         "MTR-001", 
 *         Instant.now().minus(1, ChronoUnit.DAYS),
 *         Instant.now());
 * }
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * This client is thread-safe. The underlying Hazelcast client handles
 * concurrent access efficiently.
 * 
 * @see E4sClient
 */
public class E4sHzClient implements E4sClient {

    private static final String METER_DATA_MAP = "meter-data";

    private final HazelcastInstance hazelcastClient;
    private final IMap<String, MeterBucket> meterDataMap;

    public E4sHzClient(String address) {
        this(createClientConfig(address));
    }

    public E4sHzClient(ClientConfig config) {
        this.hazelcastClient = HazelcastClient.newHazelcastClient(config);
        this.meterDataMap = hazelcastClient.getMap(METER_DATA_MAP);
    }

    public E4sHzClient(HazelcastInstance hazelcastClient) {
        this.hazelcastClient = hazelcastClient;
        this.meterDataMap = hazelcastClient.getMap(METER_DATA_MAP);
    }

    private static ClientConfig createClientConfig(String address) {
        ClientConfig config = new ClientConfig();
        
        config.getNetworkConfig()
                .addAddress(address.split(","))
                .setSmartRouting(true)
                .setConnectionTimeout(5000);
        
        config.getSerializationConfig().addSerializerConfig(
                new SerializerConfig()
                        .setTypeClass(MeterReading.class)
                        .setImplementation(new MeterReadingHazelcastSerializer())
        );
        
        config.getSerializationConfig().addSerializerConfig(
                new SerializerConfig()
                        .setTypeClass(MeterBucket.class)
                        .setImplementation(new MeterBucketHazelcastSerializer())
        );
        
        return config;
    }

    @Override
    public void ingestReading(String meterId, MeterReading reading) {
        LocalDate day = Instant.ofEpochMilli(reading.getReportedTs())
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        String key = MeterDayKey.of(meterId, day).toKeyString();

        meterDataMap.compute(key, (k, bucket) -> {
            if (bucket == null) {
                bucket = new MeterBucket(meterId, day.toEpochDay());
            }
            bucket.addReading(reading);
            return bucket;
        });

    }

    /**
     * Ingests multiple readings for a single meter with optimized batching.
     * 
     * <p>This method groups readings by day and performs a single {@link IMap#compute}
     * operation per day bucket, significantly reducing network roundtrips.
     * 
     * <p>Readings with duplicate timestamps ({@code reportedTs}) are automatically
     * replaced in the bucket.
     * 
     * @param meterId the meter identifier
     * @param readings the readings to ingest
     */
    @Override
    public void ingestReadings(String meterId, List<MeterReading> readings) {
        Map<LocalDate, List<MeterReading>> byDay = readings.stream()
                .collect(Collectors.groupingBy(r ->
                        Instant.ofEpochMilli(r.getReportedTs())
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()));

        byDay.forEach((day, dayReadings) -> {
            String key = MeterDayKey.of(meterId, day).toKeyString();
            meterDataMap.compute(key, (k, bucket) -> {
                if (bucket == null) {
                    bucket = new MeterBucket(meterId, day.toEpochDay());
                }
                for (MeterReading reading : dayReadings) {
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
    public List<MeterReading> queryRange(String meterId, Instant start, Instant end) {
        List<MeterReading> result = new ArrayList<>();
        LocalDate startDay = start.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDay = end.atZone(ZoneOffset.UTC).toLocalDate();

        LocalDate currentDay = startDay;
        while (!currentDay.isAfter(endDay)) {
            String key = MeterDayKey.of(meterId, currentDay).toKeyString();
            MeterBucket bucket = meterDataMap.get(key);
            if (bucket != null) {
                long startTs = start.toEpochMilli();
                long endTs = end.toEpochMilli();
                MeterReading[] readings = bucket.getReadings();
                for (int i = 0; i < bucket.getReadingCount(); i++) {
                    MeterReading r = readings[i];
                    if (r.getReportedTs() >= startTs && r.getReportedTs() <= endTs) {
                        result.add(r);
                    }
                }
            }
            currentDay = currentDay.plusDays(1);
        }

        result.sort(Comparator.comparingLong(MeterReading::getReportedTs));
        return result;
    }

    @Override
    public AggregationResult queryAggregation(String meterId, Instant start, Instant end,
                                               AggregationType type, Interval interval) {
        List<MeterReading> readings = queryRange(meterId, start, end);

        AggregationResult result = new AggregationResult();
        result.setMeterId(meterId);
        result.setAggregationType(type);
        result.setInterval(interval);

        if (readings.isEmpty()) {
            return result;
        }

        switch (type) {
            case AVG -> {
                double sum = 0;
                for (MeterReading r : readings) {
                    sum += r.getPower();
                }
                result.setValue(sum / readings.size());
                result.setCount(readings.size());
            }
            case SUM -> {
                double sum = 0;
                for (MeterReading r : readings) {
                    sum += r.getPower();
                }
                result.setValue(sum);
                result.setCount(readings.size());
            }
            case MIN -> {
                double min = Double.MAX_VALUE;
                for (MeterReading r : readings) {
                    if (r.getPower() < min) {
                        min = r.getPower();
                    }
                }
                result.setValue(min);
                result.setCount(readings.size());
            }
            case MAX -> {
                double max = Double.MIN_VALUE;
                for (MeterReading r : readings) {
                    if (r.getPower() > max) {
                        max = r.getPower();
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


    public IMap<String, MeterBucket> getMeterDataMap() {
        return meterDataMap;
    }
}
