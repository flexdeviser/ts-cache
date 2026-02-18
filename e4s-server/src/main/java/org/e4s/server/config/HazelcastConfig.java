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

/**
 * Hazelcast configuration for the e4s-server time-series cache.
 * 
 * <p>This configuration sets up:
 * <ul>
 *   <li>Custom Kryo + Deflater serializers for optimal memory usage</li>
 *   <li>IMap configuration for meter data storage</li>
 *   <li>Backup strategy for cluster resilience</li>
 *   <li>Eviction policy for memory management</li>
 * </ul>
 * 
 * <h2>Serialization</h2>
 * Custom serializers are registered for:
 * <ul>
 *   <li>{@link MeterReading} - TYPE_ID 2001</li>
 *   <li>{@link MeterBucket} - TYPE_ID 2002</li>
 * </ul>
 * 
 * <h2>Map Configuration</h2>
 * The "meter-data" map is configured with:
 * <ul>
 *   <li><b>In-Memory Format:</b> BINARY - stores serialized data for lower memory</li>
 *   <li><b>Backup Count:</b> 1 - one replica for cluster resilience (50% memory overhead)</li>
 *   <li><b>TTL:</b> 0 - no automatic expiration (handled by custom eviction job)</li>
 *   <li><b>Eviction:</b> LRU with 75% heap limit as safety net</li>
 * </ul>
 * 
 * <h2>Deployment</h2>
 * Currently configured for embedded mode (single node). For cluster deployment:
 * <ul>
 *   <li>Add network configuration for multicast/TCP discovery</li>
 *   <li>Consider increasing partition count for better distribution</li>
 *   <li>Backup count of 1 provides resilience with 50% memory overhead</li>
 * </ul>
 */
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
