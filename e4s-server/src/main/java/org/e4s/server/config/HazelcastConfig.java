package org.e4s.server.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.e4s.model.MeterBucket;
import org.e4s.model.MeterReading;
import org.e4s.model.serialization.MeterBucketHazelcastSerializer;
import org.e4s.model.serialization.MeterReadingHazelcastSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HazelcastConfig {

    public static final String METER_DATA_MAP = "meter-data";

    @Bean
    public HazelcastInstance hazelcastInstance() {
        Config config = new Config();
        config.setInstanceName("e4s-server");

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
