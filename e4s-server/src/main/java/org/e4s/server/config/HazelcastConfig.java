package org.e4s.server.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.e4s.model.GenericBucket;
import org.e4s.model.dynamic.DynamicModelRegistry;
import org.e4s.model.serialization.GenericBucketHazelcastSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HazelcastConfig {

    public static final String METER_DATA_MAP = "meter-data";

    @Bean
    public HazelcastInstance hazelcastInstance() {
        DynamicModelRegistry.getInstance().initialize();
        
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

        return Hazelcast.newHazelcastInstance(config);
    }
}
