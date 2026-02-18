package org.e4s.server.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class MeterBucketV2Test {

    @Test
    void testCreateEmptyBucket() {
        MeterBucketV2 bucket = new MeterBucketV2();

        assertNotNull(bucket.getReadings());
        assertEquals(0, bucket.getReadingCount());
        assertTrue(bucket.getCreatedTime() > 0);
        assertEquals(bucket.getCreatedTime(), bucket.getLastAccessTime());
    }

    @Test
    void testCreateBucketWithMeterIdAndDate() {
        long epochDay = LocalDate.of(2026, 2, 18).toEpochDay();
        MeterBucketV2 bucket = new MeterBucketV2("MTR-001", epochDay);

        assertEquals("MTR-001", bucket.getMeterId());
        assertEquals(epochDay, bucket.getBucketDateEpochDay());
        assertNotNull(bucket.getReadings());
        assertEquals(0, bucket.getReadingCount());
    }

    @Test
    void testAddReading() {
        MeterBucketV2 bucket = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay());
        MeterReadingV2 reading = new MeterReadingV2(System.currentTimeMillis(), 220.5, 5.2, 1146.6);

        bucket.addReading(reading);

        assertEquals(1, bucket.getReadingCount());
        assertEquals(reading, bucket.getReadings()[0]);
    }

    @Test
    void testAddReadings() {
        MeterBucketV2 bucket = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay());
        MeterReadingV2[] readings = new MeterReadingV2[]{
                new MeterReadingV2(System.currentTimeMillis(), 1.0, 1.0, 1.0),
                new MeterReadingV2(System.currentTimeMillis() + 900000, 1.0, 1.0, 1.0)
        };

        bucket.addReadings(readings);

        assertEquals(2, bucket.getReadingCount());
    }

    @Test
    void testTouchUpdatesLastAccessTime() throws InterruptedException {
        MeterBucketV2 bucket = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay());
        long initialAccessTime = bucket.getLastAccessTime();

        Thread.sleep(10);
        bucket.touch();

        assertTrue(bucket.getLastAccessTime() > initialAccessTime);
    }

    @Test
    void testAddReadingUpdatesLastAccessTime() throws InterruptedException {
        MeterBucketV2 bucket = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay());
        long initialAccessTime = bucket.getLastAccessTime();

        Thread.sleep(10);
        bucket.addReading(new MeterReadingV2(System.currentTimeMillis(), 1.0, 1.0, 1.0));

        assertTrue(bucket.getLastAccessTime() > initialAccessTime);
    }

    @Test
    void testSettersAndGetters() {
        MeterBucketV2 bucket = new MeterBucketV2();
        long epochDay = LocalDate.of(2026, 2, 18).toEpochDay();
        long time = System.currentTimeMillis();

        bucket.setMeterId("MTR-002");
        bucket.setBucketDateEpochDay(epochDay);
        bucket.setCreatedTime(time);
        bucket.setLastAccessTime(time + 1000);

        assertEquals("MTR-002", bucket.getMeterId());
        assertEquals(epochDay, bucket.getBucketDateEpochDay());
        assertEquals(time, bucket.getCreatedTime());
        assertEquals(time + 1000, bucket.getLastAccessTime());
    }

    @Test
    void testTrimToSize() {
        MeterBucketV2 bucket = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay(), 100);
        bucket.addReading(new MeterReadingV2(System.currentTimeMillis(), 1.0, 1.0, 1.0));
        bucket.addReading(new MeterReadingV2(System.currentTimeMillis(), 1.0, 1.0, 1.0));

        assertTrue(bucket.getReadings().length > 2);
        
        bucket.trimToSize();
        
        assertEquals(2, bucket.getReadings().length);
    }

    @Test
    void testEnsureCapacity() {
        MeterBucketV2 bucket = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay(), 2);
        
        bucket.addReading(new MeterReadingV2(System.currentTimeMillis(), 1.0, 1.0, 1.0));
        bucket.addReading(new MeterReadingV2(System.currentTimeMillis(), 1.0, 1.0, 1.0));
        
        assertEquals(2, bucket.getReadings().length);
        
        bucket.addReading(new MeterReadingV2(System.currentTimeMillis(), 1.0, 1.0, 1.0));
        
        assertTrue(bucket.getReadings().length >= 3);
    }

    @Test
    void testToString() {
        MeterBucketV2 bucket = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay());
        bucket.addReading(new MeterReadingV2(System.currentTimeMillis(), 1.0, 1.0, 1.0));

        String str = bucket.toString();
        assertTrue(str.contains("MTR-001"));
        assertTrue(str.contains("readingCount=1"));
    }
}
