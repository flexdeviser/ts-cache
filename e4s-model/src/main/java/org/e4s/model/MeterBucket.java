package org.e4s.model;

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

    public void addReading(MeterReading reading) {
        ensureCapacity(readingCount + 1);
        readings[readingCount++] = reading;
        touch();
    }

    public void addReadings(MeterReading[] newReadings) {
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
