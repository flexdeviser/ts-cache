package org.e4s.server.benchmark;

import org.e4s.server.model.MeterReadingV2;
import org.e4s.server.service.MeterCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);
    private static final Random random = new Random();

    private final MeterCacheService meterCacheService;

    public BenchmarkRunner(MeterCacheService meterCacheService) {
        this.meterCacheService = meterCacheService;
    }

    public BenchmarkResult runIngestBenchmark(BenchmarkConfig config) {
        log.info("Starting ingest benchmark with config: {}", config);
        
        List<MeterReadingV2> readings = generateReadings(config.readingsPerMeter, config.startInstant);
        ExecutorService executor = Executors.newFixedThreadPool(config.threadCount);
        
        AtomicLong totalOps = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong maxLatency = new AtomicLong(0);
        AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
        
        CountDownLatch latch = new CountDownLatch(config.threadCount);
        long benchmarkStart = System.nanoTime();
        
        for (int t = 0; t < config.threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    int meterStart = threadId * config.metersPerThread;
                    int meterEnd = meterStart + config.metersPerThread;
                    
                    for (int m = meterStart; m < meterEnd; m++) {
                        String meterId = config.meterIdPrefix + m;
                        
                        for (int r = 0; r < config.readingsPerMeter; r++) {
                            long opStart = System.nanoTime();
                            
                            meterCacheService.ingestReading(meterId, readings.get(r));
                            
                            long opLatency = System.nanoTime() - opStart;
                            totalLatency.addAndGet(opLatency);
                            maxLatency.updateAndGet(v -> Math.max(v, opLatency));
                            minLatency.updateAndGet(v -> Math.min(v, opLatency));
                            totalOps.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long benchmarkEnd = System.nanoTime();
        executor.shutdown();
        
        BenchmarkResult result = new BenchmarkResult();
        result.setOperationType("INGEST");
        result.setTotalOps(totalOps.get());
        result.setDurationMs((benchmarkEnd - benchmarkStart) / 1_000_000);
        result.setOpsPerSecond((double) totalOps.get() / ((benchmarkEnd - benchmarkStart) / 1_000_000_000.0));
        result.setAvgLatencyUs(totalLatency.get() / (double) totalOps.get() / 1000.0);
        result.setMinLatencyUs(minLatency.get() / 1000.0);
        result.setMaxLatencyUs(maxLatency.get() / 1000.0);
        result.setBucketCount(meterCacheService.getBucketCount());
        result.setMemoryBytes(meterCacheService.getMemoryUsageBytes());
        
        log.info("Ingest benchmark completed: {}", result);
        return result;
    }

    public BenchmarkResult runBatchIngestBenchmark(BenchmarkConfig config) {
        log.info("Starting batch ingest benchmark with config: {}", config);
        
        ExecutorService executor = Executors.newFixedThreadPool(config.threadCount);
        
        AtomicLong totalOps = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong maxLatency = new AtomicLong(0);
        AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
        AtomicLong totalReadings = new AtomicLong(0);
        
        CountDownLatch latch = new CountDownLatch(config.threadCount);
        long benchmarkStart = System.nanoTime();
        
        for (int t = 0; t < config.threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    int meterStart = threadId * config.metersPerThread;
                    int meterEnd = meterStart + config.metersPerThread;
                    
                    for (int m = meterStart; m < meterEnd; m++) {
                        String meterId = config.meterIdPrefix + m;
                        List<MeterReadingV2> readings = generateReadings(config.batchSize, config.startInstant);
                        
                        long opStart = System.nanoTime();
                        
                        meterCacheService.ingestReadings(meterId, readings);
                        
                        long opLatency = System.nanoTime() - opStart;
                        totalLatency.addAndGet(opLatency);
                        maxLatency.updateAndGet(v -> Math.max(v, opLatency));
                        minLatency.updateAndGet(v -> Math.min(v, opLatency));
                        totalOps.incrementAndGet();
                        totalReadings.addAndGet(readings.size());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long benchmarkEnd = System.nanoTime();
        executor.shutdown();
        
        BenchmarkResult result = new BenchmarkResult();
        result.setOperationType("BATCH_INGEST");
        result.setTotalOps(totalOps.get());
        result.setTotalReadings(totalReadings.get());
        result.setDurationMs((benchmarkEnd - benchmarkStart) / 1_000_000);
        result.setOpsPerSecond((double) totalReadings.get() / ((benchmarkEnd - benchmarkStart) / 1_000_000_000.0));
        result.setAvgLatencyUs(totalLatency.get() / (double) totalOps.get() / 1000.0);
        result.setMinLatencyUs(minLatency.get() / 1000.0);
        result.setMaxLatencyUs(maxLatency.get() / 1000.0);
        result.setBucketCount(meterCacheService.getBucketCount());
        result.setMemoryBytes(meterCacheService.getMemoryUsageBytes());
        
        log.info("Batch ingest benchmark completed: {}", result);
        return result;
    }

    public BenchmarkResult runQueryBenchmark(BenchmarkConfig config) {
        log.info("Starting query benchmark with config: {}", config);
        
        ExecutorService executor = Executors.newFixedThreadPool(config.threadCount);
        
        AtomicLong totalOps = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong maxLatency = new AtomicLong(0);
        AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
        AtomicLong totalReadings = new AtomicLong(0);
        
        CountDownLatch latch = new CountDownLatch(config.threadCount);
        long benchmarkStart = System.nanoTime();
        
        for (int t = 0; t < config.threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    int meterStart = threadId * config.metersPerThread;
                    int meterEnd = meterStart + config.metersPerThread;
                    
                    for (int i = 0; i < config.queriesPerThread; i++) {
                        int meterIndex = meterStart + random.nextInt(config.metersPerThread);
                        String meterId = config.meterIdPrefix + meterIndex;
                        
                        Instant start = config.startInstant.plus(random.nextInt(7), ChronoUnit.DAYS);
                        Instant end = start.plus(1, ChronoUnit.DAYS);
                        
                        long opStart = System.nanoTime();
                        
                        var readings = meterCacheService.queryRange(meterId, start, end);
                        
                        long opLatency = System.nanoTime() - opStart;
                        totalLatency.addAndGet(opLatency);
                        maxLatency.updateAndGet(v -> Math.max(v, opLatency));
                        minLatency.updateAndGet(v -> Math.min(v, opLatency));
                        totalOps.incrementAndGet();
                        totalReadings.addAndGet(readings.size());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long benchmarkEnd = System.nanoTime();
        executor.shutdown();
        
        BenchmarkResult result = new BenchmarkResult();
        result.setOperationType("QUERY_RANGE");
        result.setTotalOps(totalOps.get());
        result.setTotalReadings(totalReadings.get());
        result.setDurationMs((benchmarkEnd - benchmarkStart) / 1_000_000);
        result.setOpsPerSecond((double) totalOps.get() / ((benchmarkEnd - benchmarkStart) / 1_000_000_000.0));
        result.setAvgLatencyUs(totalLatency.get() / (double) totalOps.get() / 1000.0);
        result.setMinLatencyUs(minLatency.get() / 1000.0);
        result.setMaxLatencyUs(maxLatency.get() / 1000.0);
        result.setBucketCount(meterCacheService.getBucketCount());
        result.setMemoryBytes(meterCacheService.getMemoryUsageBytes());
        
        log.info("Query benchmark completed: {}", result);
        return result;
    }

    public BenchmarkResult runAggregationBenchmark(BenchmarkConfig config) {
        log.info("Starting aggregation benchmark with config: {}", config);
        
        ExecutorService executor = Executors.newFixedThreadPool(config.threadCount);
        
        AtomicLong totalOps = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong maxLatency = new AtomicLong(0);
        AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
        
        CountDownLatch latch = new CountDownLatch(config.threadCount);
        long benchmarkStart = System.nanoTime();
        
        for (int t = 0; t < config.threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    int meterStart = threadId * config.metersPerThread;
                    int meterEnd = meterStart + config.metersPerThread;
                    
                    for (int i = 0; i < config.queriesPerThread; i++) {
                        int meterIndex = meterStart + random.nextInt(config.metersPerThread);
                        String meterId = config.meterIdPrefix + meterIndex;
                        
                        Instant start = config.startInstant.plus(random.nextInt(7), ChronoUnit.DAYS);
                        Instant end = start.plus(3, ChronoUnit.DAYS);
                        
                        long opStart = System.nanoTime();
                        
                        meterCacheService.queryAggregation(meterId, start, end, 
                                MeterCacheService.AggregationType.AVG, 
                                MeterCacheService.Interval.DAILY);
                        
                        long opLatency = System.nanoTime() - opStart;
                        totalLatency.addAndGet(opLatency);
                        maxLatency.updateAndGet(v -> Math.max(v, opLatency));
                        minLatency.updateAndGet(v -> Math.min(v, opLatency));
                        totalOps.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long benchmarkEnd = System.nanoTime();
        executor.shutdown();
        
        BenchmarkResult result = new BenchmarkResult();
        result.setOperationType("AGGREGATION");
        result.setTotalOps(totalOps.get());
        result.setDurationMs((benchmarkEnd - benchmarkStart) / 1_000_000);
        result.setOpsPerSecond((double) totalOps.get() / ((benchmarkEnd - benchmarkStart) / 1_000_000_000.0));
        result.setAvgLatencyUs(totalLatency.get() / (double) totalOps.get() / 1000.0);
        result.setMinLatencyUs(minLatency.get() / 1000.0);
        result.setMaxLatencyUs(maxLatency.get() / 1000.0);
        result.setBucketCount(meterCacheService.getBucketCount());
        result.setMemoryBytes(meterCacheService.getMemoryUsageBytes());
        
        log.info("Aggregation benchmark completed: {}", result);
        return result;
    }

    private List<MeterReadingV2> generateReadings(int count, Instant start) {
        List<MeterReadingV2> readings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long reportedTs = start.plus(i * 15, ChronoUnit.MINUTES).toEpochMilli();
            readings.add(new MeterReadingV2(
                    reportedTs,
                    220 + random.nextDouble() * 10,
                    5 + random.nextDouble() * 2,
                    1000 + random.nextDouble() * 500
            ));
        }
        return readings;
    }

    public static class BenchmarkConfig {
        private String meterIdPrefix = "MTR-";
        private int threadCount = 4;
        private int metersPerThread = 100;
        private int readingsPerMeter = 96;
        private int batchSize = 96;
        private int queriesPerThread = 100;
        private Instant startInstant = Instant.now().minus(14, ChronoUnit.DAYS);

        public String getMeterIdPrefix() {
            return meterIdPrefix;
        }

        public void setMeterIdPrefix(String meterIdPrefix) {
            this.meterIdPrefix = meterIdPrefix;
        }

        public int getThreadCount() {
            return threadCount;
        }

        public void setThreadCount(int threadCount) {
            this.threadCount = threadCount;
        }

        public int getMetersPerThread() {
            return metersPerThread;
        }

        public void setMetersPerThread(int metersPerThread) {
            this.metersPerThread = metersPerThread;
        }

        public int getReadingsPerMeter() {
            return readingsPerMeter;
        }

        public void setReadingsPerMeter(int readingsPerMeter) {
            this.readingsPerMeter = readingsPerMeter;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getQueriesPerThread() {
            return queriesPerThread;
        }

        public void setQueriesPerThread(int queriesPerThread) {
            this.queriesPerThread = queriesPerThread;
        }

        public Instant getStartInstant() {
            return startInstant;
        }

        public void setStartInstant(Instant startInstant) {
            this.startInstant = startInstant;
        }

        @Override
        public String toString() {
            return "BenchmarkConfig{" +
                    "meterIdPrefix='" + meterIdPrefix + '\'' +
                    ", threadCount=" + threadCount +
                    ", metersPerThread=" + metersPerThread +
                    ", readingsPerMeter=" + readingsPerMeter +
                    ", batchSize=" + batchSize +
                    ", queriesPerThread=" + queriesPerThread +
                    '}';
        }
    }

    public static class BenchmarkResult {
        private String operationType;
        private long totalOps;
        private long totalReadings;
        private long durationMs;
        private double opsPerSecond;
        private double avgLatencyUs;
        private double minLatencyUs;
        private double maxLatencyUs;
        private long bucketCount;
        private long memoryBytes;

        public String getOperationType() {
            return operationType;
        }

        public void setOperationType(String operationType) {
            this.operationType = operationType;
        }

        public long getTotalOps() {
            return totalOps;
        }

        public void setTotalOps(long totalOps) {
            this.totalOps = totalOps;
        }

        public long getTotalReadings() {
            return totalReadings;
        }

        public void setTotalReadings(long totalReadings) {
            this.totalReadings = totalReadings;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }

        public double getOpsPerSecond() {
            return opsPerSecond;
        }

        public void setOpsPerSecond(double opsPerSecond) {
            this.opsPerSecond = opsPerSecond;
        }

        public double getAvgLatencyUs() {
            return avgLatencyUs;
        }

        public void setAvgLatencyUs(double avgLatencyUs) {
            this.avgLatencyUs = avgLatencyUs;
        }

        public double getMinLatencyUs() {
            return minLatencyUs;
        }

        public void setMinLatencyUs(double minLatencyUs) {
            this.minLatencyUs = minLatencyUs;
        }

        public double getMaxLatencyUs() {
            return maxLatencyUs;
        }

        public void setMaxLatencyUs(double maxLatencyUs) {
            this.maxLatencyUs = maxLatencyUs;
        }

        public long getBucketCount() {
            return bucketCount;
        }

        public void setBucketCount(long bucketCount) {
            this.bucketCount = bucketCount;
        }

        public long getMemoryBytes() {
            return memoryBytes;
        }

        public void setMemoryBytes(long memoryBytes) {
            this.memoryBytes = memoryBytes;
        }

        public double getMemoryMB() {
            return memoryBytes / (1024.0 * 1024.0);
        }

        @Override
        public String toString() {
            return String.format(
                    "BenchmarkResult{type=%s, ops=%d, readings=%d, duration=%dms, ops/sec=%.2f, " +
                    "latency[avg=%.2fµs, min=%.2fµs, max=%.2fµs], buckets=%d, memory=%.2fMB}",
                    operationType, totalOps, totalReadings, durationMs, opsPerSecond,
                    avgLatencyUs, minLatencyUs, maxLatencyUs, bucketCount, getMemoryMB());
        }
    }
}
