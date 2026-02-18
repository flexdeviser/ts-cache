package org.e4s.server.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeterBucketTest {

    @Test
    void testCreateEmptyBucket() {
        MeterBucket bucket = new MeterBucket();

        assertNotNull(bucket.getReadings());
        assertTrue(bucket.getReadings().isEmpty());
        assertTrue(bucket.getCreatedTime() > 0);
        assertEquals(bucket.getCreatedTime(), bucket.getLastAccessTime());
    }

    @Test
    void testCreateBucketWithMeterIdAndDate() {
        LocalDate date = LocalDate.of(2026, 2, 18);
        MeterBucket bucket = new MeterBucket("MTR-001", date);

        assertEquals("MTR-001", bucket.getMeterId());
        assertEquals(date, bucket.getBucketDate());
        assertNotNull(bucket.getReadings());
        assertTrue(bucket.getReadings().isEmpty());
    }

    @Test
    void testAddReading() {
        MeterBucket bucket = new MeterBucket("MTR-001", LocalDate.of(2026, 2, 18));
        MeterReading reading = new MeterReading(Instant.now(), 
                new BigDecimal("220.5"), 
                new BigDecimal("5.2"), 
                new BigDecimal("1146.6"));

        bucket.addReading(reading);

        assertEquals(1, bucket.getReadings().size());
        assertEquals(reading, bucket.getReadings().get(0));
    }

    @Test
    void testAddReadings() {
        MeterBucket bucket = new MeterBucket("MTR-001", LocalDate.of(2026, 2, 18));
        List<MeterReading> readings = Arrays.asList(
                new MeterReading(Instant.now(), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE),
                new MeterReading(Instant.now().plusSeconds(900), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)
        );

        bucket.addReadings(readings);

        assertEquals(2, bucket.getReadings().size());
    }

    @Test
    void testTouchUpdatesLastAccessTime() throws InterruptedException {
        MeterBucket bucket = new MeterBucket("MTR-001", LocalDate.of(2026, 2, 18));
        long initialAccessTime = bucket.getLastAccessTime();

        Thread.sleep(10);
        bucket.touch();

        assertTrue(bucket.getLastAccessTime() > initialAccessTime);
    }

    @Test
    void testAddReadingUpdatesLastAccessTime() throws InterruptedException {
        MeterBucket bucket = new MeterBucket("MTR-001", LocalDate.of(2026, 2, 18));
        long initialAccessTime = bucket.getLastAccessTime();

        Thread.sleep(10);
        bucket.addReading(new MeterReading(Instant.now(), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));

        assertTrue(bucket.getLastAccessTime() > initialAccessTime);
    }

    @Test
    void testSettersAndGetters() {
        MeterBucket bucket = new MeterBucket();
        LocalDate date = LocalDate.of(2026, 2, 18);
        long time = System.currentTimeMillis();

        bucket.setMeterId("MTR-002");
        bucket.setBucketDate(date);
        bucket.setCreatedTime(time);
        bucket.setLastAccessTime(time + 1000);

        assertEquals("MTR-002", bucket.getMeterId());
        assertEquals(date, bucket.getBucketDate());
        assertEquals(time, bucket.getCreatedTime());
        assertEquals(time + 1000, bucket.getLastAccessTime());
    }

    @Test
    void testToString() {
        MeterBucket bucket = new MeterBucket("MTR-001", LocalDate.of(2026, 2, 18));
        bucket.addReading(new MeterReading(Instant.now(), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));

        String str = bucket.toString();
        assertTrue(str.contains("MTR-001"));
        assertTrue(str.contains("2026-02-18"));
        assertTrue(str.contains("readingCount=1"));
    }
}
