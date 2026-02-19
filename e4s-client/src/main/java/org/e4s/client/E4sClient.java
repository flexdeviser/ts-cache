package org.e4s.client;

import org.e4s.model.Timestamped;

import java.time.Instant;
import java.util.List;

public interface E4sClient extends AutoCloseable {

    void ingestReading(String meterId, Timestamped reading);

    void ingestReadings(String meterId, List<? extends Timestamped> readings);

    void ingestBatch(List<IngestRequest> requests);

    List<? extends Timestamped> queryRange(String meterId, Instant start, Instant end);

    AggregationResult queryAggregation(String meterId, Instant start, Instant end,
                                        AggregationType type, Interval interval);

    CacheStats getCacheStats();

    long getBucketCount();

    boolean isHealthy();

    class IngestRequest {
        private String meterId;
        private List<? extends Timestamped> readings;

        public IngestRequest() {
        }

        public IngestRequest(String meterId, List<? extends Timestamped> readings) {
            this.meterId = meterId;
            this.readings = readings;
        }

        public String getMeterId() {
            return meterId;
        }

        public void setMeterId(String meterId) {
            this.meterId = meterId;
        }

        public List<? extends Timestamped> getReadings() {
            return readings;
        }

        public void setReadings(List<? extends Timestamped> readings) {
            this.readings = readings;
        }
    }

    enum AggregationType {
        SUM, AVG, MIN, MAX, COUNT
    }

    enum Interval {
        HOURLY, DAILY, WEEKLY
    }

    class AggregationResult {
        private String meterId;
        private AggregationType aggregationType;
        private Interval interval;
        private Double value;
        private int count;

        public String getMeterId() {
            return meterId;
        }

        public void setMeterId(String meterId) {
            this.meterId = meterId;
        }

        public AggregationType getAggregationType() {
            return aggregationType;
        }

        public void setAggregationType(AggregationType aggregationType) {
            this.aggregationType = aggregationType;
        }

        public Interval getInterval() {
            return interval;
        }

        public void setInterval(Interval interval) {
            this.interval = interval;
        }

        public Double getValue() {
            return value;
        }

        public void setValue(Double value) {
            this.value = value;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    class CacheStats {
        private long totalEntries;
        private long ownedEntries;
        private long memoryBytes;
        private double memoryMB;
        private double memoryGB;
        private long putCount;
        private long getCount;

        public long getTotalEntries() {
            return totalEntries;
        }

        public void setTotalEntries(long totalEntries) {
            this.totalEntries = totalEntries;
        }

        public long getOwnedEntries() {
            return ownedEntries;
        }

        public void setOwnedEntries(long ownedEntries) {
            this.ownedEntries = ownedEntries;
        }

        public long getMemoryBytes() {
            return memoryBytes;
        }

        public void setMemoryBytes(long memoryBytes) {
            this.memoryBytes = memoryBytes;
        }

        public double getMemoryMB() {
            return memoryMB;
        }

        public void setMemoryMB(double memoryMB) {
            this.memoryMB = memoryMB;
        }

        public double getMemoryGB() {
            return memoryGB;
        }

        public void setMemoryGB(double memoryGB) {
            this.memoryGB = memoryGB;
        }

        public long getPutCount() {
            return putCount;
        }

        public void setPutCount(long putCount) {
            this.putCount = putCount;
        }

        public long getGetCount() {
            return getCount;
        }

        public void setGetCount(long getCount) {
            this.getCount = getCount;
        }
    }
}
