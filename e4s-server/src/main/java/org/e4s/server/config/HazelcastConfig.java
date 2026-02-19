package org.e4s.server.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.ReplicatedMapConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.replicatedmap.ReplicatedMap;
import org.e4s.model.GenericBucket;
import org.e4s.model.dynamic.DynamicModelRegistry;
import org.e4s.model.serialization.GenericBucketHazelcastSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class HazelcastConfig {

    public static final String METER_DATA_MAP = "meter-data";
    public static final String MODEL_INFO_MAP = "e4s-model-info";
    public static final String MODEL_HASH_KEY = "modelHash";
    public static final String MODEL_NAMES_KEY = "modelNames";
    public static final String MODEL_PATH_KEY = "modelPath";

    @Bean
    public HazelcastInstance hazelcastInstance() {
        DynamicModelRegistry registry = DynamicModelRegistry.getInstance();
        registry.initialize();
        
        Config config = new Config();
        config.setInstanceName("e4s-server");

        config.getSerializationConfig().addSerializerConfig(
                new SerializerConfig()
                        .setTypeClass(GenericBucket.class)
                        .setImplementation(new GenericBucketHazelcastSerializer())
        );

        MapConfig meterDataMapConfig = new MapConfig(METER_DATA_MAP);
        meterDataMapConfig.setInMemoryFormat(com.hazelcast.config.InMemoryFormat.BINARY);
        meterDataMapConfig.setBackupCount(1);
        meterDataMapConfig.setTimeToLiveSeconds(0);
        meterDataMapConfig.setMaxIdleSeconds(0);
        meterDataMapConfig.setStatisticsEnabled(true);
        meterDataMapConfig.getEvictionConfig()
                .setEvictionPolicy(com.hazelcast.config.EvictionPolicy.LRU)
                .setSize(75)
                .setMaxSizePolicy(com.hazelcast.config.MaxSizePolicy.USED_HEAP_PERCENTAGE);

        config.addMapConfig(meterDataMapConfig);

        ReplicatedMapConfig modelInfoConfig = new ReplicatedMapConfig(MODEL_INFO_MAP);
        config.addReplicatedMapConfig(modelInfoConfig);

        HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);
        
        publishModelInfo(hz, registry);
        
        return hz;
    }

    private void publishModelInfo(HazelcastInstance hz, DynamicModelRegistry registry) {
        ReplicatedMap<String, Object> modelInfo = hz.getReplicatedMap(MODEL_INFO_MAP);
        
        modelInfo.put(MODEL_HASH_KEY, registry.getModelsHash());
        modelInfo.put(MODEL_NAMES_KEY, new HashMap<>(Map.of("names", String.join(",", registry.getModelNames()))));
        modelInfo.put(MODEL_PATH_KEY, registry.getModelsPath());
        
        System.out.println("Published model info to cluster:");
        System.out.println("  Hash: " + registry.getModelsHash().substring(0, 16) + "...");
        System.out.println("  Models: " + registry.getModelNames());
    }
}
