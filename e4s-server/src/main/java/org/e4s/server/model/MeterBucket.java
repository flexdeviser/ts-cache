package org.e4s.server.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MeterBucket implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String meterId;
    private LocalDate bucketDate;
    private List<MeterReading> readings;
    private long lastAccessTime;
    private long createdTime;

    public MeterBucket() {
        this.readings = new ArrayList<>();
        this.createdTime = System.currentTimeMillis();
        this.lastAccessTime = this.createdTime;
    }

    public MeterBucket(String meterId, LocalDate bucketDate) {
        this();
        this.meterId = meterId;
        this.bucketDate = bucketDate;
    }

    public String getMeterId() {
        return meterId;
    }

    public void setMeterId(String meterId) {
        this.meterId = meterId;
    }

    public LocalDate getBucketDate() {
        return bucketDate;
    }

    public void setBucketDate(LocalDate bucketDate) {
        this.bucketDate = bucketDate;
    }

    public List<MeterReading> getReadings() {
        return readings;
    }

    public void setReadings(List<MeterReading> readings) {
        this.readings = readings;
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
        this.readings.add(reading);
        touch();
    }

    public void addReadings(List<MeterReading> newReadings) {
        this.readings.addAll(newReadings);
        touch();
    }

    @Override
    public String toString() {
        return "MeterBucket{" +
                "meterId='" + meterId + '\'' +
                ", bucketDate=" + bucketDate +
                ", readingCount=" + readings.size() +
                ", lastAccessTime=" + lastAccessTime +
                ", createdTime=" + createdTime +
                '}';
    }
}
