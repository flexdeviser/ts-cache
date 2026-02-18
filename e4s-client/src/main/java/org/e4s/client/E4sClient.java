package org.e4s.client;

import org.e4s.model.MeterReading;

import java.time.Instant;
import java.util.List;

/**
 * Client interface for interacting with the e4s-server time-series cache.
 * 
 * <p>This interface provides a consistent API for:
 * <ul>
 *   <li><b>Data Ingestion:</b> Single reading, batch per meter, multi-meter batch</li>
 *   <li><b>Query:</b> Time-range queries and aggregations</li>
 *   <li><b>Monitoring:</b> Health checks and cache statistics</li>
 * </ul>
 * 
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link org.e4s.client.http.E4sHttpClient} - HTTP REST client using JSON</li>
 *   <li><i>Future: Hazelcast native client with binary serialization</i></li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (E4sClient client = new E4sHttpClient("http://localhost:8080")) {
 *     // Ingest data
 *     MeterReading reading = new MeterReading(
 *         Instant.now().toEpochMilli(), 220.5, 5.2, 1146.6);
 *     client.ingestReading("MTR-001", reading);
 *     
 *     // Query data
 *     List<MeterReading> data = client.queryRange(
 *         "MTR-001", 
 *         Instant.now().minus(1, ChronoUnit.DAYS),
 *         Instant.now());
 *     
 *     // Get aggregation
 *     AggregationResult avg = client.queryAggregation(
 *         "MTR-001", start, end,
 *         AggregationType.AVG, Interval.HOURLY);
 * }
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * Implementations are expected to be thread-safe. A single client instance
 * can be shared across multiple threads.
 * 
 * @see org.e4s.client.http.E4sHttpClient
 */
public interface E4sClient extends AutoCloseable {

    /**
     * Ingest a single meter reading.
     *
     * @param meterId the meter identifier
     * @param reading the meter reading to ingest
     */
    void ingestReading(String meterId, MeterReading reading);

    /**
     * Ingest multiple readings for a single meter.
     *
     * @param meterId the meter identifier
     * @param readings list of readings to ingest
     */
    void ingestReadings(String meterId, List<MeterReading> readings);

    /**
     * Ingest readings for multiple meters in a single batch.
     *
     * @param requests list of ingest requests, each containing meterId and readings
     */
    void ingestBatch(List<IngestRequest> requests);

    /**
     * Query meter readings within a time range.
     *
     * @param meterId the meter identifier
     * @param start start of the time range (inclusive)
     * @param end end of the time range (inclusive)
     * @return list of readings within the time range, sorted by timestamp
     */
    List<MeterReading> queryRange(String meterId, Instant start, Instant end);

    /**
     * Query aggregated meter data.
     *
     * @param meterId the meter identifier
     * @param start start of the time range (inclusive)
     * @param end end of the time range (inclusive)
     * @param type aggregation type (SUM, AVG, MIN, MAX, COUNT)
     * @param interval aggregation interval (HOURLY, DAILY, WEEKLY)
     * @return aggregation result with value and count
     */
    AggregationResult queryAggregation(String meterId, Instant start, Instant end,
                                        AggregationType type, Interval interval);

    /**
     * Get cache statistics.
     *
     * @return cache statistics including entry count, memory usage, operation counts
     */
    CacheStats getCacheStats();

    /**
     * Get the total number of buckets in the cache.
     *
     * @return total bucket count
     */
    long getBucketCount();

    /**
     * Check if the server is healthy and responding.
     *
     * @return true if server is healthy, false otherwise
     */
    boolean isHealthy();

    /**
     * Request for batch ingestion containing multiple meters.
     */
    class IngestRequest {
        private String meterId;
        private List<MeterReading> readings;

        public IngestRequest() {
        }

        public IngestRequest(String meterId, List<MeterReading> readings) {
            this.meterId = meterId;
            this.readings = readings;
        }

        public String getMeterId() {
            return meterId;
        }

        public void setMeterId(String meterId) {
            this.meterId = meterId;
        }

        public List<MeterReading> getReadings() {
            return readings;
        }

        public void setReadings(List<MeterReading> readings) {
            this.readings = readings;
        }
    }

    /**
     * Aggregation type for query operations.
     */
    enum AggregationType {
        SUM, AVG, MIN, MAX, COUNT
    }

    /**
     * Interval for aggregation grouping.
     */
    enum Interval {
        HOURLY, DAILY, WEEKLY
    }

    /**
     * Result of an aggregation query.
     */
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

    /**
     * Cache statistics from the server.
     */
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
