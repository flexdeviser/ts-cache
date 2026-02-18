package org.e4s.server.service;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.e4s.model.MeterBucket;
import org.e4s.model.MeterDayKey;
import org.e4s.model.MeterReading;
import org.e4s.server.config.HazelcastConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeterCacheServiceTest {

    private HazelcastInstance hazelcastInstance;
    private MeterCacheService meterCacheService;

    @BeforeEach
    void setUp() {
        hazelcastInstance = Hazelcast.newHazelcastInstance();
        meterCacheService = new MeterCacheService(hazelcastInstance);
    }

    @AfterEach
    void tearDown() {
        hazelcastInstance.shutdown();
    }

    @Test
    void testIngestSingleReading() {
        long now = System.currentTimeMillis();
        MeterReading reading = new MeterReading(now, 220.5, 5.2, 1146.6);

        meterCacheService.ingestReading("MTR-001", reading);

        IMap<String, MeterBucket> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        assertEquals(1, map.size());

        LocalDate day = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate();
        String expectedKey = "MTR-001:" + day;
        MeterBucket bucket = map.get(expectedKey);
        assertNotNull(bucket);
        assertEquals("MTR-001", bucket.getMeterId());
        assertEquals(1, bucket.getReadingCount());
    }

    @Test
    void testIngestMultipleReadingsForSameDay() {
        long base = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            MeterReading reading = new MeterReading(
                    base + i * 15 * 60 * 1000,
                    1.0, 1.0, 1.0);
            meterCacheService.ingestReading("MTR-001", reading);
        }

        IMap<String, MeterBucket> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        assertEquals(1, map.size());
        
        MeterBucket bucket = map.values().iterator().next();
        assertEquals(5, bucket.getReadingCount());
    }

    @Test
    void testIngestReadingsAcrossMultipleDays() {
        long day1 = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        long day2 = Instant.parse("2026-02-19T10:00:00Z").toEpochMilli();

        meterCacheService.ingestReading("MTR-001", new MeterReading(day1, 1.0, 1.0, 1.0));
        meterCacheService.ingestReading("MTR-001", new MeterReading(day2, 1.0, 1.0, 1.0));

        IMap<String, MeterBucket> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        assertEquals(2, map.size());
    }

    @Test
    void testIngestReadingsBatch() {
        List<MeterReading> readings = Arrays.asList(
                new MeterReading(System.currentTimeMillis(), 1.0, 1.0, 1.0),
                new MeterReading(System.currentTimeMillis() + 900000, 1.0, 1.0, 1.0),
                new MeterReading(System.currentTimeMillis() + 1800000, 1.0, 1.0, 1.0)
        );

        meterCacheService.ingestReadings("MTR-001", readings);

        IMap<String, MeterBucket> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        assertEquals(1, map.size());
        assertEquals(3, map.values().iterator().next().getReadingCount());
    }

    @Test
    void testIngestBatch() {
        MeterCacheService.IngestRequest request1 = new MeterCacheService.IngestRequest();
        request1.setMeterId("MTR-001");
        request1.setReadings(Arrays.asList(
                new MeterReading(System.currentTimeMillis(), 1.0, 1.0, 1.0)
        ));

        MeterCacheService.IngestRequest request2 = new MeterCacheService.IngestRequest();
        request2.setMeterId("MTR-002");
        request2.setReadings(Arrays.asList(
                new MeterReading(System.currentTimeMillis(), 1.0, 1.0, 1.0)
        ));

        meterCacheService.ingestBatch(Arrays.asList(request1, request2));

        IMap<String, MeterBucket> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        assertEquals(2, map.size());
    }

    @Test
    void testQueryRangeSingleDay() {
        long base = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        for (int i = 0; i < 10; i++) {
            MeterReading reading = new MeterReading(
                    base + i * 15 * 60 * 1000,
                    1.0, 1.0, 1.0);
            meterCacheService.ingestReading("MTR-001", reading);
        }

        List<MeterReading> result = meterCacheService.queryRange("MTR-001",
                Instant.ofEpochMilli(base), Instant.ofEpochMilli(base + 3 * 60 * 60 * 1000));

        assertEquals(10, result.size());
    }

    @Test
    void testQueryRangeMultipleDays() {
        long day1 = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        long day2 = Instant.parse("2026-02-19T10:00:00Z").toEpochMilli();

        meterCacheService.ingestReading("MTR-001", new MeterReading(day1, 1.0, 1.0, 1.0));
        meterCacheService.ingestReading("MTR-001", new MeterReading(day2, 1.0, 1.0, 1.0));

        List<MeterReading> result = meterCacheService.queryRange("MTR-001",
                Instant.ofEpochMilli(day1 - 3600000), Instant.ofEpochMilli(day2 + 3600000));

        assertEquals(2, result.size());
    }

    @Test
    void testQueryRangeEmptyResult() {
        List<MeterReading> result = meterCacheService.queryRange("MTR-999",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());

        assertTrue(result.isEmpty());
    }

    @Test
    void testQueryAggregationAvg() {
        long base = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        for (int i = 0; i < 5; i++) {
            MeterReading reading = new MeterReading(
                    base + i * 15 * 60 * 1000,
                    1.0, 1.0, 100 + i * 10);
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
            MeterReading reading = new MeterReading(
                    base + i * 15 * 60 * 1000,
                    1.0, 1.0, 100);
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
            MeterReading reading = new MeterReading(
                    base + i * 15 * 60 * 1000,
                    1.0, 1.0, 100 + i * 50);
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
            MeterReading reading = new MeterReading(
                    base + i * 15 * 60 * 1000,
                    1.0, 1.0, 100 + i * 50);
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
            MeterReading reading = new MeterReading(
                    base + i * 15 * 60 * 1000,
                    1.0, 1.0, 1.0);
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

        meterCacheService.ingestReading("MTR-001", 
                new MeterReading(System.currentTimeMillis(), 1.0, 1.0, 1.0));

        assertEquals(1, meterCacheService.getBucketCount());
    }

    @Test
    void testEvictBucket() {
        long now = System.currentTimeMillis();
        meterCacheService.ingestReading("MTR-001", 
                new MeterReading(now, 1.0, 1.0, 1.0));

        assertEquals(1, meterCacheService.getBucketCount());

        LocalDate day = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate();
        meterCacheService.evictBucket("MTR-001", day);

        assertEquals(0, meterCacheService.getBucketCount());
    }

    @Test
    void testEvictOldBuckets() throws InterruptedException {
        IMap<String, MeterBucket> map = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
        
        MeterBucket oldBucket = new MeterBucket("MTR-001", LocalDate.now().minusDays(30).toEpochDay());
        oldBucket.setCreatedTime(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);
        oldBucket.setLastAccessTime(System.currentTimeMillis() - 48 * 60 * 60 * 1000);
        map.put("MTR-001:" + LocalDate.now().minusDays(30), oldBucket);

        MeterBucket recentBucket = new MeterBucket("MTR-002", LocalDate.now().toEpochDay());
        map.put("MTR-002:" + LocalDate.now(), recentBucket);

        assertEquals(2, meterCacheService.getBucketCount());

        meterCacheService.evictOldBuckets(21, 24);

        assertEquals(1, meterCacheService.getBucketCount());
    }

    @Test
    void testGetCacheStats() {
        long now = System.currentTimeMillis();
        meterCacheService.ingestReading("MTR-001", 
                new MeterReading(now, 220.5, 5.2, 1146.6));

        MeterCacheService.CacheStats stats = meterCacheService.getCacheStats();

        assertEquals(1, stats.getTotalEntries());
        assertNotNull(stats);
    }
}
