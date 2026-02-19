package org.e4s.server.service;

import com.hazelcast.config.Config;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.e4s.model.GenericBucket;
import org.e4s.model.Timestamped;
import org.e4s.model.dynamic.DynamicModelRegistry;
import org.e4s.model.serialization.GenericBucketHazelcastSerializer;
import org.e4s.server.config.HazelcastConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MeterCacheServiceTest {

    private HazelcastInstance hazelcastInstance;
    private MeterCacheService meterCacheService;

    @BeforeEach
    void setUp() {
        DynamicModelRegistry.getInstance().initialize();
        
        Config config = new Config();
        config.getSerializationConfig().addSerializerConfig(
                new SerializerConfig()
                        .setTypeClass(GenericBucket.class)
                        .setImplementation(new GenericBucketHazelcastSerializer())
        );
        
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        meterCacheService = new MeterCacheService(hazelcastInstance);
    }

    @AfterEach
    void tearDown() {
        hazelcastInstance.shutdown();
    }

    @Test
    void testIngestSingleReading() {
        long now = System.currentTimeMillis();
        Map<String, Object> fieldValues = new HashMap<>();
        fieldValues.put("reportedTs", now);
        fieldValues.put("voltage", 220.5);
        fieldValues.put("current", 5.2);
        fieldValues.put("power", 1146.6);
        Timestamped reading = DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues);

        meterCacheService.ingestReading("MTR-001", reading);

        IMap<String, GenericBucket<Timestamped>> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        assertEquals(1, map.size());

        LocalDate day = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate();
        String expectedKey = "MTR-001:" + day;
        GenericBucket<Timestamped> bucket = map.get(expectedKey);
        assertNotNull(bucket);
        assertEquals("MTR-001", bucket.getId());
        assertEquals(1, bucket.getReadingCount());
    }

    @Test
    void testIngestMultipleReadingsForSameDay() {
        long base = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            fieldValues.put("reportedTs", base + i * 15 * 60 * 1000);
            fieldValues.put("voltage", 1.0);
            fieldValues.put("current", 1.0);
            fieldValues.put("power", 1.0);
            Timestamped reading = DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues);
            meterCacheService.ingestReading("MTR-001", reading);
        }

        IMap<String, GenericBucket<Timestamped>> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        assertEquals(1, map.size());
        
        GenericBucket<Timestamped> bucket = map.values().iterator().next();
        assertEquals(5, bucket.getReadingCount());
    }

    @Test
    void testIngestReadingsAcrossMultipleDays() {
        long day1 = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        long day2 = Instant.parse("2026-02-19T10:00:00Z").toEpochMilli();

        Map<String, Object> fv1 = new HashMap<>();
        fv1.put("reportedTs", day1);
        fv1.put("voltage", 1.0);
        fv1.put("current", 1.0);
        fv1.put("power", 1.0);
        meterCacheService.ingestReading("MTR-001", DynamicModelRegistry.getInstance().createReading("MeterReading", fv1));
        Map<String, Object> fv2 = new HashMap<>();
        fv2.put("reportedTs", day2);
        fv2.put("voltage", 1.0);
        fv2.put("current", 1.0);
        fv2.put("power", 1.0);
        meterCacheService.ingestReading("MTR-001", DynamicModelRegistry.getInstance().createReading("MeterReading", fv2));

        IMap<String, GenericBucket<Timestamped>> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        assertEquals(2, map.size());
    }

    @Test
    void testIngestReadingsBatch() {
        Map<String, Object> fieldValues1 = new HashMap<>();
        fieldValues1.put("reportedTs", System.currentTimeMillis());
        fieldValues1.put("voltage", 1.0);
        fieldValues1.put("current", 1.0);
        fieldValues1.put("power", 1.0);
        Map<String, Object> fieldValues2 = new HashMap<>();
        fieldValues2.put("reportedTs", System.currentTimeMillis() + 900000);
        fieldValues2.put("voltage", 1.0);
        fieldValues2.put("current", 1.0);
        fieldValues2.put("power", 1.0);
        Map<String, Object> fieldValues3 = new HashMap<>();
        fieldValues3.put("reportedTs", System.currentTimeMillis() + 1800000);
        fieldValues3.put("voltage", 1.0);
        fieldValues3.put("current", 1.0);
        fieldValues3.put("power", 1.0);
        List<Timestamped> readings = Arrays.asList(
                DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues1),
                DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues2),
                DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues3)
        );

        meterCacheService.ingestReadings("MTR-001", readings);

        IMap<String, GenericBucket<Timestamped>> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        assertEquals(1, map.size());
        assertEquals(3, map.values().iterator().next().getReadingCount());
    }

    @Test
    void testIngestBatch() {
        MeterCacheService.IngestRequest request1 = new MeterCacheService.IngestRequest();
        request1.setMeterId("MTR-001");
        Map<String, Object> rv1 = new HashMap<>();
        rv1.put("reportedTs", System.currentTimeMillis());
        rv1.put("voltage", 1.0);
        rv1.put("current", 1.0);
        rv1.put("power", 1.0);
        request1.setReadings(Arrays.asList(
                DynamicModelRegistry.getInstance().createReading("MeterReading", rv1)
        ));

        MeterCacheService.IngestRequest request2 = new MeterCacheService.IngestRequest();
        request2.setMeterId("MTR-002");
        Map<String, Object> rv2 = new HashMap<>();
        rv2.put("reportedTs", System.currentTimeMillis());
        rv2.put("voltage", 1.0);
        rv2.put("current", 1.0);
        rv2.put("power", 1.0);
        request2.setReadings(Arrays.asList(
                DynamicModelRegistry.getInstance().createReading("MeterReading", rv2)
        ));

        meterCacheService.ingestBatch(Arrays.asList(request1, request2));

        IMap<String, GenericBucket<Timestamped>> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        assertEquals(2, map.size());
    }

    @Test
    void testQueryRangeSingleDay() {
        long base = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            fieldValues.put("reportedTs", base + i * 15 * 60 * 1000);
            fieldValues.put("voltage", 1.0);
            fieldValues.put("current", 1.0);
            fieldValues.put("power", 1.0);
            Timestamped reading = DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues);
            meterCacheService.ingestReading("MTR-001", reading);
        }

        List<Timestamped> result = meterCacheService.queryRange("MTR-001",
                Instant.ofEpochMilli(base), Instant.ofEpochMilli(base + 3 * 60 * 60 * 1000));

        assertEquals(10, result.size());
    }

    @Test
    void testQueryRangeMultipleDays() {
        long day1 = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        long day2 = Instant.parse("2026-02-19T10:00:00Z").toEpochMilli();

        Map<String, Object> fv1 = new HashMap<>();
        fv1.put("reportedTs", day1);
        fv1.put("voltage", 1.0);
        fv1.put("current", 1.0);
        fv1.put("power", 1.0);
        meterCacheService.ingestReading("MTR-001", DynamicModelRegistry.getInstance().createReading("MeterReading", fv1));
        Map<String, Object> fv2 = new HashMap<>();
        fv2.put("reportedTs", day2);
        fv2.put("voltage", 1.0);
        fv2.put("current", 1.0);
        fv2.put("power", 1.0);
        meterCacheService.ingestReading("MTR-001", DynamicModelRegistry.getInstance().createReading("MeterReading", fv2));

        List<Timestamped> result = meterCacheService.queryRange("MTR-001",
                Instant.ofEpochMilli(day1 - 3600000), Instant.ofEpochMilli(day2 + 3600000));

        assertEquals(2, result.size());
    }

    @Test
    void testQueryRangeEmptyResult() {
        List<Timestamped> result = meterCacheService.queryRange("MTR-999",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());

        assertTrue(result.isEmpty());
    }

    @Test
    void testQueryAggregationAvg() {
        long base = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            fieldValues.put("reportedTs", base + i * 15 * 60 * 1000);
            fieldValues.put("voltage", 1.0);
            fieldValues.put("current", 1.0);
            fieldValues.put("power", 100 + i * 10);
            Timestamped reading = DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues);
            meterCacheService.ingestReading("MTR-001", reading);
        }

        MeterCacheService.AggregationResult result = meterCacheService.queryAggregation(
                "MTR-001", Instant.ofEpochMilli(base), Instant.ofEpochMilli(base + 2 * 60 * 60 * 1000),
                MeterCacheService.AggregationType.AVG,
                MeterCacheService.Interval.HOURLY);

        assertEquals("MTR-001", result.getMeterId());
        assertEquals(MeterCacheService.AggregationType.AVG, result.getAggregationType());
        assertEquals(5, result.getCount());
        assertEquals(120.0, result.getValue(), 0.01);
    }

    @Test
    void testQueryAggregationSum() {
        long base = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            fieldValues.put("reportedTs", base + i * 15 * 60 * 1000);
            fieldValues.put("voltage", 1.0);
            fieldValues.put("current", 1.0);
            fieldValues.put("power", 100);
            Timestamped reading = DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues);
            meterCacheService.ingestReading("MTR-001", reading);
        }

        MeterCacheService.AggregationResult result = meterCacheService.queryAggregation(
                "MTR-001", Instant.ofEpochMilli(base), Instant.ofEpochMilli(base + 2 * 60 * 60 * 1000),
                MeterCacheService.AggregationType.SUM,
                MeterCacheService.Interval.HOURLY);

        assertEquals(300.0, result.getValue(), 0.01);
        assertEquals(3, result.getCount());
    }

    @Test
    void testQueryAggregationMin() {
        long base = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            fieldValues.put("reportedTs", base + i * 15 * 60 * 1000);
            fieldValues.put("voltage", 1.0);
            fieldValues.put("current", 1.0);
            fieldValues.put("power", 100 + i * 50);
            Timestamped reading = DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues);
            meterCacheService.ingestReading("MTR-001", reading);
        }

        MeterCacheService.AggregationResult result = meterCacheService.queryAggregation(
                "MTR-001", Instant.ofEpochMilli(base), Instant.ofEpochMilli(base + 2 * 60 * 60 * 1000),
                MeterCacheService.AggregationType.MIN,
                MeterCacheService.Interval.HOURLY);

        assertEquals(100.0, result.getValue(), 0.01);
    }

    @Test
    void testQueryAggregationMax() {
        long base = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            fieldValues.put("reportedTs", base + i * 15 * 60 * 1000);
            fieldValues.put("voltage", 1.0);
            fieldValues.put("current", 1.0);
            fieldValues.put("power", 100 + i * 50);
            Timestamped reading = DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues);
            meterCacheService.ingestReading("MTR-001", reading);
        }

        MeterCacheService.AggregationResult result = meterCacheService.queryAggregation(
                "MTR-001", Instant.ofEpochMilli(base), Instant.ofEpochMilli(base + 2 * 60 * 60 * 1000),
                MeterCacheService.AggregationType.MAX,
                MeterCacheService.Interval.HOURLY);

        assertEquals(200.0, result.getValue(), 0.01);
    }

    @Test
    void testQueryAggregationCount() {
        long base = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> fieldValues = new HashMap<>();
            fieldValues.put("reportedTs", base + i * 15 * 60 * 1000);
            fieldValues.put("voltage", 1.0);
            fieldValues.put("current", 1.0);
            fieldValues.put("power", 1.0);
            Timestamped reading = DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues);
            meterCacheService.ingestReading("MTR-001", reading);
        }

        MeterCacheService.AggregationResult result = meterCacheService.queryAggregation(
                "MTR-001", Instant.ofEpochMilli(base), Instant.ofEpochMilli(base + 2 * 60 * 60 * 1000),
                MeterCacheService.AggregationType.COUNT,
                MeterCacheService.Interval.HOURLY);

        assertEquals(5.0, result.getValue(), 0.01);
        assertEquals(5, result.getCount());
    }

    @Test
    void testQueryAggregationEmpty() {
        MeterCacheService.AggregationResult result = meterCacheService.queryAggregation(
                "MTR-999", Instant.now().minus(1, ChronoUnit.DAYS), Instant.now(),
                MeterCacheService.AggregationType.AVG,
                MeterCacheService.Interval.HOURLY);

        assertEquals("MTR-999", result.getMeterId());
        assertNull(result.getValue());
    }

    @Test
    void testGetBucketCount() {
        assertEquals(0, meterCacheService.getBucketCount());

        Map<String, Object> fieldValues = new HashMap<>();
        fieldValues.put("reportedTs", System.currentTimeMillis());
        fieldValues.put("voltage", 1.0);
        fieldValues.put("current", 1.0);
        fieldValues.put("power", 1.0);
        meterCacheService.ingestReading("MTR-001", 
                DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues));

        assertEquals(1, meterCacheService.getBucketCount());
    }

    @Test
    void testEvictBucket() {
        long now = System.currentTimeMillis();
        Map<String, Object> fv = new HashMap<>();
        fv.put("reportedTs", now);
        fv.put("voltage", 1.0);
        fv.put("current", 1.0);
        fv.put("power", 1.0);
        meterCacheService.ingestReading("MTR-001", 
                DynamicModelRegistry.getInstance().createReading("MeterReading", fv));

        assertEquals(1, meterCacheService.getBucketCount());

        LocalDate day = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate();
        meterCacheService.evictBucket("MTR-001", day);

        assertEquals(0, meterCacheService.getBucketCount());
    }

    @Test
    void testEvictOldBuckets() throws InterruptedException {
        IMap<String, GenericBucket<Timestamped>> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        
        GenericBucket<Timestamped> oldBucket = DynamicModelRegistry.getInstance().createBucket("MeterReading", "MTR-001", LocalDate.now().minusDays(30).toEpochDay());
        oldBucket.setCreatedTime(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);
        oldBucket.setLastAccessTime(System.currentTimeMillis() - 48 * 60 * 60 * 1000);
        map.put("MTR-001:" + LocalDate.now().minusDays(30), oldBucket);

        GenericBucket<Timestamped> recentBucket = DynamicModelRegistry.getInstance().createBucket("MeterReading", "MTR-002", LocalDate.now().toEpochDay());
        map.put("MTR-002:" + LocalDate.now(), recentBucket);

        assertEquals(2, meterCacheService.getBucketCount());

        meterCacheService.evictOldBuckets(21, 24);

        assertEquals(1, meterCacheService.getBucketCount());
    }

    @Test
    void testGetCacheStats() {
        long now = System.currentTimeMillis();
        Map<String, Object> fieldValues = new HashMap<>();
        fieldValues.put("reportedTs", now);
        fieldValues.put("voltage", 220.5);
        fieldValues.put("current", 5.2);
        fieldValues.put("power", 1146.6);
        meterCacheService.ingestReading("MTR-001", 
                DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues));

        MeterCacheService.CacheStats stats = meterCacheService.getCacheStats();

        assertEquals(1, stats.getTotalEntries());
        assertNotNull(stats);
    }
}
