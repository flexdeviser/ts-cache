package org.e4s.model;

import java.lang.reflect.Array;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GenericBucket<T extends Timestamped> {

    private String meterId;
    private long bucketDateEpochDay;
    private T[] readings;
    private int readingCount;
    private long lastAccessTime;
    private long createdTime;
    private final Class<T> readingType;

    @SuppressWarnings("unchecked")
    public GenericBucket(Class<T> readingType) {
        this.readingType = readingType;
        this.readings = (T[]) Array.newInstance(readingType, 0);
        this.readingCount = 0;
        this.createdTime = System.currentTimeMillis();
        this.lastAccessTime = this.createdTime;
    }

    public GenericBucket(String meterId, long bucketDateEpochDay, Class<T> readingType) {
        this(readingType);
        this.meterId = meterId;
        this.bucketDateEpochDay = bucketDateEpochDay;
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

    public T[] getReadings() {
        return readings;
    }

    @SuppressWarnings("unchecked")
    public void setReadings(T[] readings) {
        this.readings = readings != null ? readings : (T[]) Array.newInstance(readingType, 0);
        this.readingCount = this.readings.length;
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

    public void addReading(T reading) {
        long newTimestamp = reading.getTimestamp();

        for (int i = 0; i < readingCount; i++) {
            if (readings[i].getTimestamp() == newTimestamp) {
                readings[i] = reading;
                touch();
                return;
            }
        }

        ensureCapacity(readingCount + 1);
        readings[readingCount++] = reading;
        touch();
    }

    @SuppressWarnings("unchecked")
    public void addReadings(T[] newReadings) {
        if (newReadings == null || newReadings.length == 0) {
            return;
        }
        for (T reading : newReadings) {
            addReading(reading);
        }
    }

    @SuppressWarnings("unchecked")
    private void ensureCapacity(int minCapacity) {
        if (readings == null || readings.length == 0) {
            readings = (T[]) Array.newInstance(readingType, Math.max(minCapacity, 16));
            return;
        }
        if (readings.length < minCapacity) {
            int newCapacity = Math.max(minCapacity, readings.length + (readings.length >> 1));
            T[] newReadings = (T[]) Array.newInstance(readingType, newCapacity);
            System.arraycopy(readings, 0, newReadings, 0, readingCount);
            readings = newReadings;
        }
    }

    @SuppressWarnings("unchecked")
    public void trimToSize() {
        if (readings != null && readings.length > readingCount) {
            if (readingCount == 0) {
                readings = (T[]) Array.newInstance(readingType, 0);
            } else {
                T[] trimmed = (T[]) Array.newInstance(readingType, readingCount);
                System.arraycopy(readings, 0, trimmed, 0, readingCount);
                readings = trimmed;
            }
        }
    }

    public List<T> queryRange(long startTs, long endTs) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < readingCount; i++) {
            T r = readings[i];
            long ts = r.getTimestamp();
            if (ts >= startTs && ts <= endTs) {
                result.add(r);
            }
        }
        result.sort(Comparator.comparingLong(Timestamped::getTimestamp));
        return result;
    }

    @Override
    public String toString() {
        return "GenericBucket{" +
                "meterId='" + meterId + '\'' +
                ", bucketDateEpochDay=" + bucketDateEpochDay +
                ", readingCount=" + readingCount +
                '}';
    }
}
