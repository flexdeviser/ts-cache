package org.e4s.server.model;

public class MeterBucketV2 {

    private String meterId;
    private long bucketDateEpochDay;
    private MeterReadingV2[] readings;
    private int readingCount;
    private long lastAccessTime;
    private long createdTime;

    public MeterBucketV2() {
        this.readings = new MeterReadingV2[0];
        this.readingCount = 0;
        this.createdTime = System.currentTimeMillis();
        this.lastAccessTime = this.createdTime;
    }

    public MeterBucketV2(String meterId, long bucketDateEpochDay) {
        this();
        this.meterId = meterId;
        this.bucketDateEpochDay = bucketDateEpochDay;
    }

    public MeterBucketV2(String meterId, long bucketDateEpochDay, int initialCapacity) {
        this.meterId = meterId;
        this.bucketDateEpochDay = bucketDateEpochDay;
        this.readings = new MeterReadingV2[initialCapacity];
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

    public MeterReadingV2[] getReadings() {
        return readings;
    }

    public void setReadings(MeterReadingV2[] readings) {
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

    public void addReading(MeterReadingV2 reading) {
        ensureCapacity(readingCount + 1);
        readings[readingCount++] = reading;
        touch();
    }

    public void addReadings(MeterReadingV2[] newReadings) {
        if (newReadings == null || newReadings.length == 0) {
            return;
        }
        ensureCapacity(readingCount + newReadings.length);
        System.arraycopy(newReadings, 0, readings, readingCount, newReadings.length);
        readingCount += newReadings.length;
        touch();
    }

    private void ensureCapacity(int minCapacity) {
        if (readings == null) {
            readings = new MeterReadingV2[Math.max(minCapacity, 16)];
            return;
        }
        if (readings.length < minCapacity) {
            int newCapacity = Math.max(minCapacity, readings.length + (readings.length >> 1));
            MeterReadingV2[] newReadings = new MeterReadingV2[newCapacity];
            System.arraycopy(readings, 0, newReadings, 0, readingCount);
            readings = newReadings;
        }
    }

    public void trimToSize() {
        if (readings != null && readings.length > readingCount) {
            if (readingCount == 0) {
                readings = new MeterReadingV2[0];
            } else {
                MeterReadingV2[] trimmed = new MeterReadingV2[readingCount];
                System.arraycopy(readings, 0, trimmed, 0, readingCount);
                readings = trimmed;
            }
        }
    }

    @Override
    public String toString() {
        return "MeterBucketV2{" +
                "meterId='" + meterId + '\'' +
                ", bucketDateEpochDay=" + bucketDateEpochDay +
                ", readingCount=" + readingCount +
                ", lastAccessTime=" + lastAccessTime +
                ", createdTime=" + createdTime +
                '}';
    }
}
