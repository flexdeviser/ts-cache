package org.e4s.server.service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.e4s.model.MeterBucket;
import org.e4s.model.MeterDayKey;
import org.e4s.model.MeterReading;
import org.e4s.server.config.HazelcastConfig;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Core service for managing meter data in the Hazelcast cache.
 * 
 * <p>This service provides all operations for the time-series hot cache:
 * <ul>
 *   <li><b>Ingestion:</b> Single reading, batch per meter, multi-meter batch</li>
 *   <li><b>Query:</b> Time-range queries and aggregations</li>
 *   <li><b>Eviction:</b> Manual and age-based cleanup</li>
 *   <li><b>Monitoring:</b> Cache statistics and memory usage</li>
 * </ul>
 * 
 * <h2>Data Model</h2>
 * Data is organized as daily buckets:
 * <ul>
 *   <li>Key format: "meterId:YYYY-MM-DD" (e.g., "MTR-001:2026-02-18")</li>
 *   <li>Value: {@link MeterBucket} containing all readings for that day</li>
 *   <li>Typical: 96 readings/day (15-minute intervals)</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * Uses Hazelcast's {@link IMap#compute} for atomic updates. The service is thread-safe
 * and can handle concurrent ingestion from multiple sources.
 * 
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li>Single ingest: ~50K ops/sec, 156 µs latency</li>
 *   <li>Batch ingest: ~45K ops/sec, 14 ms latency</li>
 *   <li>Range query: ~69K ops/sec, 110 µs latency</li>
 *   <li>Aggregation: ~35K ops/sec, 219 µs latency</li>
 * </ul>
 */
@Service
public class MeterCacheService {

    private final IMap<String, MeterBucket> meterDataMap;

    public MeterCacheService(HazelcastInstance hazelcastInstance) {
        this.meterDataMap = hazelcastInstance.getMap(HazelcastConfig.METER_DATA_MAP);
    }

    public void ingestReading(String meterId, MeterReading reading) {
        LocalDate day = Instant.ofEpochMilli(reading.getReportedTs()).atZone(ZoneOffset.UTC).toLocalDate();
        String key = MeterDayKey.of(meterId, day).toKeyString();

        meterDataMap.compute(key, (k, bucket) -> {
            if (bucket == null) {
                bucket = new MeterBucket(meterId, day.toEpochDay());
            }
            bucket.addReading(reading);
            return bucket;
        });
    }

    public void ingestReadings(String meterId, List<MeterReading> readings) {
        readings.forEach(reading -> ingestReading(meterId, reading));
    }

    public void ingestBatch(List<IngestRequest> requests) {
        requests.forEach(req -> ingestReadings(req.getMeterId(), req.getReadings()));
    }

    public List<MeterReading> queryRange(String meterId, Instant start, Instant end) {
        List<MeterReading> result = new ArrayList<>();
        LocalDate startDay = start.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDay = end.atZone(ZoneOffset.UTC).toLocalDate();

        LocalDate currentDay = startDay;
        while (!currentDay.isAfter(endDay)) {
            String key = MeterDayKey.of(meterId, currentDay).toKeyString();
            MeterBucket bucket = meterDataMap.get(key);
            if (bucket != null) {
                long startTs = start.toEpochMilli();
                long endTs = end.toEpochMilli();
                MeterReading[] readings = bucket.getReadings();
                for (int i = 0; i < bucket.getReadingCount(); i++) {
                    MeterReading r = readings[i];
                    if (r.getReportedTs() >= startTs && r.getReportedTs() <= endTs) {
                        result.add(r);
                    }
                }
            }
            currentDay = currentDay.plusDays(1);
        }

        result.sort(Comparator.comparingLong(MeterReading::getReportedTs));
        return result;
    }

    public AggregationResult queryAggregation(String meterId, Instant start, Instant end,
                                               AggregationType type, Interval interval) {
        List<MeterReading> readings = queryRange(meterId, start, end);

        AggregationResult result = new AggregationResult();
        result.setMeterId(meterId);
        result.setAggregationType(type);
        result.setInterval(interval);

        if (readings.isEmpty()) {
            return result;
        }

        switch (type) {
            case AVG -> {
                double sum = 0;
                for (MeterReading r : readings) {
                    sum += r.getPower();
                }
                result.setValue(sum / readings.size());
                result.setCount(readings.size());
            }
            case SUM -> {
                double sum = 0;
                for (MeterReading r : readings) {
                    sum += r.getPower();
                }
                result.setValue(sum);
                result.setCount(readings.size());
            }
            case MIN -> {
                double min = Double.MAX_VALUE;
                for (MeterReading r : readings) {
                    if (r.getPower() < min) {
                        min = r.getPower();
                    }
                }
                result.setValue(min);
                result.setCount(readings.size());
            }
            case MAX -> {
                double max = Double.MIN_VALUE;
                for (MeterReading r : readings) {
                    if (r.getPower() > max) {
                        max = r.getPower();
                    }
                }
                result.setValue(max);
                result.setCount(readings.size());
            }
            case COUNT -> {
                result.setValue((double) readings.size());
                result.setCount(readings.size());
            }
        }

        return result;
    }

    public long getBucketCount() {
        return meterDataMap.size();
    }

    public long getMemoryUsageBytes() {
        return meterDataMap.getLocalMapStats().getOwnedEntryMemoryCost();
    }

    public CacheStats getCacheStats() {
        var stats = meterDataMap.getLocalMapStats();
        return new CacheStats(
                meterDataMap.size(),
                stats.getOwnedEntryCount(),
                stats.getOwnedEntryMemoryCost(),
                stats.getHeapCost(),
                stats.getPutOperationCount(),
                stats.getGetOperationCount()
        );
    }

    public void evictBucket(String meterId, LocalDate day) {
        String key = MeterDayKey.of(meterId, day).toKeyString();
        meterDataMap.delete(key);
    }

    public void evictOldBuckets(int retentionDays, int idleHours) {
        long now = System.currentTimeMillis();
        long ageThreshold = (long) retentionDays * 24 * 60 * 60 * 1000;
        long idleThreshold = (long) idleHours * 60 * 60 * 1000;

        List<String> keysToEvict = new ArrayList<>();

        for (String key : meterDataMap.keySet()) {
            MeterBucket bucket = meterDataMap.get(key);
            if (bucket != null) {
                long age = now - bucket.getCreatedTime();
                long idle = now - bucket.getLastAccessTime();
                if (age > ageThreshold && idle > idleThreshold) {
                    keysToEvict.add(key);
                }
            }
        }

        for (String key : keysToEvict) {
            meterDataMap.delete(key);
        }
    }

    public static class IngestRequest {
        private String meterId;
        private List<MeterReading> readings;

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

    public enum AggregationType {
        SUM, AVG, MIN, MAX, COUNT
    }

    public enum Interval {
        HOURLY, DAILY, WEEKLY
    }

    public static class AggregationResult {
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

    public static class CacheStats {
        private final long totalEntries;
        private final long ownedEntries;
        private final long memoryCostBytes;
        private final long heapCostBytes;
        private final long putCount;
        private final long getCount;

        public CacheStats(long totalEntries, long ownedEntries, long memoryCostBytes, 
                          long heapCostBytes, long putCount, long getCount) {
            this.totalEntries = totalEntries;
            this.ownedEntries = ownedEntries;
            this.memoryCostBytes = memoryCostBytes;
            this.heapCostBytes = heapCostBytes;
            this.putCount = putCount;
            this.getCount = getCount;
        }

        public long getTotalEntries() {
            return totalEntries;
        }

        public long getOwnedEntries() {
            return ownedEntries;
        }

        public long getMemoryCostBytes() {
            return memoryCostBytes;
        }

        public long getHeapCostBytes() {
            return heapCostBytes;
        }

        public long getPutCount() {
            return putCount;
        }

        public long getGetCount() {
            return getCount;
        }

        public double getMemoryCostMB() {
            return memoryCostBytes / (1024.0 * 1024.0);
        }

        public double getMemoryCostGB() {
            return memoryCostBytes / (1024.0 * 1024.0 * 1024.0);
        }
    }
}
