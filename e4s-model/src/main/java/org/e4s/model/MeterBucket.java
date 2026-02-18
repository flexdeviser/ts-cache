package org.e4s.model;

/**
 * A daily bucket that stores all meter readings for a single meter on a single day.
 * 
 * <p>This is the primary storage unit in the cache system. Each bucket contains:
 * <ul>
 *   <li>meterId - The unique identifier for the meter</li>
 *   <li>bucketDateEpochDay - The date of this bucket (stored as epoch day for efficiency)</li>
 *   <li>readings - Array of readings for this day (typically 96 for 15-minute intervals)</li>
 *   <li>readingCount - Number of readings in the array</li>
 *   <li>lastAccessTime - Timestamp of last read/write (for eviction decisions)</li>
 *   <li>createdTime - Timestamp when bucket was created (for age-based eviction)</li>
 * </ul>
 * 
 * <p>Memory optimization strategies:
 * <ul>
 *   <li>Uses primitive array instead of ArrayList to avoid capacity overhead</li>
 *   <li>Stores date as epoch day (long) instead of LocalDate object</li>
 *   <li>Provides {@link #trimToSize()} to release unused array capacity before serialization</li>
 *   <li>Typical compressed size: ~1.5-2 KB for 96 readings</li>
 * </ul>
 * 
 * <p>Key design: The composite key format is "meterId:YYYY-MM-DD" (e.g., "MTR-001:2026-02-18").
 * This enables efficient prefix scanning for time-range queries on a single meter.
 * 
 * @see MeterReading
 * @see MeterDayKey
 */
public class MeterBucket {

    private String meterId;
    private long bucketDateEpochDay;
    private MeterReading[] readings;
    private int readingCount;
    private long lastAccessTime;
    private long createdTime;

    public MeterBucket() {
        this.readings = new MeterReading[0];
        this.readingCount = 0;
        this.createdTime = System.currentTimeMillis();
        this.lastAccessTime = this.createdTime;
    }

    public MeterBucket(String meterId, long bucketDateEpochDay) {
        this();
        this.meterId = meterId;
        this.bucketDateEpochDay = bucketDateEpochDay;
    }

    public MeterBucket(String meterId, long bucketDateEpochDay, int initialCapacity) {
        this.meterId = meterId;
        this.bucketDateEpochDay = bucketDateEpochDay;
        this.readings = new MeterReading[initialCapacity];
        this.readingCount = 0;
        this.createdTime = System.currentTimeMillis();
        this.lastAccessTime = this.createdTime;
    }

    public String getMeterId() {
        return meterId;
    }

    public void setMeterId(String meterId) {
        this.meterId = meterId;
    }

    public long getBucketDateEpochDay() {
        return bucketDateEpochDay;
    }

    public void setBucketDateEpochDay(long bucketDateEpochDay) {
        this.bucketDateEpochDay = bucketDateEpochDay;
    }

    public MeterReading[] getReadings() {
        return readings;
    }

    public void setReadings(MeterReading[] readings) {
        this.readings = readings;
        this.readingCount = readings != null ? readings.length : 0;
    }

    public int getReadingCount() {
        return readingCount;
    }

    public void setReadingCount(int readingCount) {
        this.readingCount = readingCount;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public void touch() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    /**
     * Adds a reading to this bucket, replacing any existing reading with the same timestamp.
     * 
     * <p>This method performs deduplication based on {@code reportedTs}:
     * <ul>
     *   <li>If a reading with the same {@code reportedTs} exists, it is replaced</li>
     *   <li>Otherwise, the new reading is appended to the array</li>
     * </ul>
     * 
     * <p>Time complexity: O(n) where n is the current reading count. This is acceptable
     * since buckets typically contain ~96 readings (15-minute intervals over 24 hours).
     * 
     * @param reading the meter reading to add or use as replacement
     */
    public void addReading(MeterReading reading) {
        long reportedTs = reading.getReportedTs();
        for (int i = 0; i < readingCount; i++) {
            if (readings[i].getReportedTs() == reportedTs) {
                readings[i] = reading;
                touch();
                return;
            }
        }
        ensureCapacity(readingCount + 1);
        readings[readingCount++] = reading;
        touch();
    }

    /**
     * Adds multiple readings to this bucket with deduplication.
     * 
     * <p>Each reading is processed through {@link #addReading(MeterReading)},
     * which handles duplicate timestamp replacement.
     * 
     * @param newReadings the readings to add
     */
    public void addReadings(MeterReading[] newReadings) {
        if (newReadings == null || newReadings.length == 0) {
            return;
        }
        for (MeterReading reading : newReadings) {
            addReading(reading);
        }
    }

    private void ensureCapacity(int minCapacity) {
        if (readings == null) {
            readings = new MeterReading[Math.max(minCapacity, 16)];
            return;
        }
        if (readings.length < minCapacity) {
            int newCapacity = Math.max(minCapacity, readings.length + (readings.length >> 1));
            MeterReading[] newReadings = new MeterReading[newCapacity];
            System.arraycopy(readings, 0, newReadings, 0, readingCount);
            readings = newReadings;
        }
    }

    public void trimToSize() {
        if (readings != null && readings.length > readingCount) {
            if (readingCount == 0) {
                readings = new MeterReading[0];
            } else {
                MeterReading[] trimmed = new MeterReading[readingCount];
                System.arraycopy(readings, 0, trimmed, 0, readingCount);
                readings = trimmed;
            }
        }
    }

    @Override
    public String toString() {
        return "MeterBucket{" +
                "meterId='" + meterId + '\'' +
                ", bucketDateEpochDay=" + bucketDateEpochDay +
                ", readingCount=" + readingCount +
                '}';
    }
}
