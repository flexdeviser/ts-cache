package org.e4s.client.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.replicatedmap.ReplicatedMap;
import org.e4s.client.AggregationResult;
import org.e4s.client.AggregationType;
import org.e4s.client.CacheStats;
import org.e4s.client.E4sClient;
import org.e4s.client.IngestRequest;
import org.e4s.client.Interval;
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
    private static final String MODEL_INFO_MAP = "e4s-model-info";
    private static final String MODEL_HASH_KEY = "modelHash";

    private final HazelcastInstance hazelcastClient;
    private final IMap<String, GenericBucket<Timestamped>> meterDataMap;
    private final String modelName;
    private final String aggregationField;

    public E4sHzClient(String address) {
        this(address, null, null, null);
    }

    public E4sHzClient(String address, String modelsPath) {
        this(address, modelsPath, null, null);
    }

    public E4sHzClient(String address, String modelsPath, String modelName) {
        this(address, modelsPath, modelName, null);
    }

    public E4sHzClient(String address, String modelsPath, String modelName, String aggregationField) {
        this(createClientConfig(address), modelsPath, modelName, aggregationField);
    }

    public E4sHzClient(ClientConfig config) {
        this(config, null, null, null);
    }

    public E4sHzClient(ClientConfig config, String modelsPath) {
        this(config, modelsPath, null, null);
    }

    public E4sHzClient(ClientConfig config, String modelsPath, String modelName) {
        this(config, modelsPath, modelName, null);
    }

    public E4sHzClient(ClientConfig config, String modelsPath, String modelName, String aggregationField) {
        DynamicModelRegistry.getInstance().initialize(modelsPath);
        this.hazelcastClient = HazelcastClient.newHazelcastClient(config);
        this.meterDataMap = hazelcastClient.getMap(METER_DATA_MAP);
        this.modelName = modelName != null ? modelName : "MeterReading";
        this.aggregationField = aggregationField != null ? aggregationField : "power";
        
        validateModelsMatchServer();
    }

    public E4sHzClient(HazelcastInstance hazelcastClient) {
        this(hazelcastClient, null, null, null);
    }

    public E4sHzClient(HazelcastInstance hazelcastClient, String modelsPath) {
        this(hazelcastClient, modelsPath, null, null);
    }

    public E4sHzClient(HazelcastInstance hazelcastClient, String modelsPath, String modelName) {
        this(hazelcastClient, modelsPath, modelName, null);
    }

    public E4sHzClient(HazelcastInstance hazelcastClient, String modelsPath, String modelName, String aggregationField) {
        DynamicModelRegistry.getInstance().initialize(modelsPath);
        this.hazelcastClient = hazelcastClient;
        this.meterDataMap = hazelcastClient.getMap(METER_DATA_MAP);
        this.modelName = modelName != null ? modelName : "MeterReading";
        this.aggregationField = aggregationField != null ? aggregationField : "power";
        
        validateModelsMatchServer();
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

    private void validateModelsMatchServer() {
        try {
            ReplicatedMap<String, Object> modelInfo = hazelcastClient.getReplicatedMap(MODEL_INFO_MAP);
            Object serverHash = modelInfo.get(MODEL_HASH_KEY);
            
            if (serverHash == null) {
                System.err.println("WARNING: Server model hash not found. Skipping validation.");
                System.err.println("  Ensure server is running with model info publishing enabled.");
                return;
            }
            
            String clientHash = DynamicModelRegistry.getInstance().getModelsHash();
            
            if (!serverHash.equals(clientHash)) {
                String error = """

                    ================================================================================
                    FATAL: Model definition mismatch between client and server
                    ================================================================================
                    
                      Server model hash: %s
                      Client model hash: %s
                    
                      This means the client and server are using different models.xml files.
                      
                      Possible causes:
                      1. Client and server have different versions of models.xml
                      2. models.xml file has been modified on one side but not the other
                      3. Wrong models.xml path configured
                    
                      Solution:
                      1. Ensure both client and server use the SAME models.xml file
                      2. Copy the correct models.xml to both locations
                      3. Restart both server and client
                    
                      Server models info endpoint: GET /api/v1/models/info
                    ================================================================================
                    """.formatted(
                        serverHash.toString().substring(0, Math.min(16, serverHash.toString().length())) + "...",
                        clientHash.substring(0, 16) + "..."
                    );
                
                System.err.println(error);
                
                hazelcastClient.shutdown();
                
                throw new IllegalStateException("Model definition mismatch - client and server must use the same models.xml");
            }
            
            System.out.println("Model validation passed:");
            System.out.println("  Hash: " + clientHash.substring(0, 16) + "...");
            System.out.println("  Models: " + DynamicModelRegistry.getInstance().getModelNames());
            System.out.println("  Using model: " + modelName);
            System.out.println("  Aggregation field: " + aggregationField);
            
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("WARNING: Failed to validate models with server: " + e.getMessage());
            System.err.println("  Continuing without validation...");
        }
    }

    public String getModelName() {
        return modelName;
    }

    public String getAggregationField() {
        return aggregationField;
    }

    @Override
    public void ingestReading(String meterId, Timestamped reading) {
        LocalDate day = Instant.ofEpochMilli(reading.getTimestamp())
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        String key = MeterDayKey.of(meterId, day).toKeyString();

        meterDataMap.compute(key, (k, bucket) -> {
            if (bucket == null) {
                bucket = DynamicModelRegistry.getInstance().createBucket(modelName, meterId, day.toEpochDay());
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
                    bucket = DynamicModelRegistry.getInstance().createBucket(modelName, meterId, day.toEpochDay());
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
                    sum += (Double) registry.getFieldValue(r, aggregationField);
                }
                result.setValue(sum / readings.size());
                result.setCount(readings.size());
            }
            case SUM -> {
                double sum = 0;
                for (Timestamped r : readings) {
                    sum += (Double) registry.getFieldValue(r, aggregationField);
                }
                result.setValue(sum);
                result.setCount(readings.size());
            }
            case MIN -> {
                double min = Double.MAX_VALUE;
                for (Timestamped r : readings) {
                    double val = (Double) registry.getFieldValue(r, aggregationField);
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
                    double val = (Double) registry.getFieldValue(r, aggregationField);
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
