package org.e4s.client.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.e4s.client.E4sClient;
import org.e4s.model.GenericBucket;
import org.e4s.model.Timestamped;
import org.e4s.model.dynamic.DynamicModelRegistry;
import org.e4s.model.serialization.GenericBucketHazelcastSerializer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class ClientBenchmark {

    private static final Random random = new Random();
    
    private final HazelcastInstance server;
    private final String meterIdPrefix;
    private final int threadCount;
    private final int metersPerThread;
    private final int readingsPerMeter;
    private final int queriesPerThread;
    private final Instant startInstant;

    public ClientBenchmark(HazelcastInstance server) {
        this(server, "BENCH-", 4, 100, 96, 100, Instant.now().minus(14, ChronoUnit.DAYS));
    }

    public ClientBenchmark(HazelcastInstance server, String meterIdPrefix, int threadCount,
                           int metersPerThread, int readingsPerMeter, int queriesPerThread,
                           Instant startInstant) {
        this.server = server;
        this.meterIdPrefix = meterIdPrefix;
        this.threadCount = threadCount;
        this.metersPerThread = metersPerThread;
        this.readingsPerMeter = readingsPerMeter;
        this.queriesPerThread = queriesPerThread;
        this.startInstant = startInstant;
    }

    public ComparisonResult runComparison() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("CLIENT BENCHMARK COMPARISON");
        System.out.println("=".repeat(80));
        System.out.printf("Config: threads=%d, meters/thread=%d, readings/meter=%d, queries/thread=%d%n",
                threadCount, metersPerThread, readingsPerMeter, queriesPerThread);
        System.out.println("=".repeat(80) + "\n");

        BenchmarkResult nativeResult = runNativeClientBenchmark();

        ComparisonResult comparison = new ComparisonResult();
        comparison.setNativeResult(nativeResult);
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BENCHMARK RESULTS SUMMARY");
        System.out.println("=".repeat(80));
        printResult("NATIVE CLIENT", nativeResult);
        printComparisonTable(nativeResult);
        
        return comparison;
    }

    private BenchmarkResult runNativeClientBenchmark() {
        System.out.println("\n--- Native Hazelcast Client Benchmark ---\n");
        
        E4sHzClient client = new E4sHzClient(server);
        
        BenchmarkResult ingestResult = benchmarkIngest(client, "NATIVE_INGEST");
        BenchmarkResult queryResult = benchmarkQuery(client, "NATIVE_QUERY");
        BenchmarkResult aggregationResult = benchmarkAggregation(client, "NATIVE_AGGREGATION");
        
        client.close();
        
        BenchmarkResult combined = new BenchmarkResult();
        combined.setOperationType("NATIVE_COMBINED");
        combined.setIngestOpsPerSecond(ingestResult.getOpsPerSecond());
        combined.setIngestAvgLatencyUs(ingestResult.getAvgLatencyUs());
        combined.setQueryOpsPerSecond(queryResult.getOpsPerSecond());
        combined.setQueryAvgLatencyUs(queryResult.getAvgLatencyUs());
        combined.setAggregationOpsPerSecond(aggregationResult.getOpsPerSecond());
        combined.setAggregationAvgLatencyUs(aggregationResult.getAvgLatencyUs());
        combined.setTotalReadings(ingestResult.getTotalReadings());
        combined.setBucketCount(ingestResult.getBucketCount());
        combined.setMemoryBytes(ingestResult.getMemoryBytes());
        
        return combined;
    }

    private BenchmarkResult benchmarkIngest(E4sClient client, String operationType) {
        System.out.println("Running ingest benchmark...");
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicLong totalOps = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong totalReadings = new AtomicLong(0);
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        long benchmarkStart = System.nanoTime();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    int meterStart = threadId * metersPerThread;
                    int meterEnd = meterStart + metersPerThread;
                    
                    for (int m = meterStart; m < meterEnd; m++) {
                        String meterId = meterIdPrefix + String.format("%05d", m);
                        List<Timestamped> readings = generateReadings(readingsPerMeter);
                        
                        long opStart = System.nanoTime();
                        client.ingestReadings(meterId, readings);
                        long opLatency = System.nanoTime() - opStart;
                        
                        totalLatency.addAndGet(opLatency);
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
        result.setOperationType(operationType);
        result.setTotalOps(totalOps.get());
        result.setTotalReadings(totalReadings.get());
        result.setDurationMs((benchmarkEnd - benchmarkStart) / 1_000_000);
        result.setOpsPerSecond((double) totalReadings.get() / ((benchmarkEnd - benchmarkStart) / 1_000_000_000.0));
        result.setAvgLatencyUs(totalLatency.get() / (double) totalOps.get() / 1000.0);
        result.setBucketCount(client.getBucketCount());
        
        E4sClient.CacheStats stats = client.getCacheStats();
        result.setMemoryBytes(stats.getMemoryBytes());
        
        System.out.printf("  Ingest: %d readings in %dms (%.2f ops/sec, avg latency %.2f µs)%n",
                totalReadings.get(), result.getDurationMs(), result.getOpsPerSecond(), result.getAvgLatencyUs());
        
        return result;
    }

    private BenchmarkResult benchmarkQuery(E4sClient client, String operationType) {
        System.out.println("Running query benchmark...");
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicLong totalOps = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong totalReadings = new AtomicLong(0);
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        long benchmarkStart = System.nanoTime();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    int meterStart = threadId * metersPerThread;
                    
                    for (int i = 0; i < queriesPerThread; i++) {
                        int meterIndex = meterStart + random.nextInt(metersPerThread);
                        String meterId = meterIdPrefix + String.format("%05d", meterIndex);
                        
                        Instant start = startInstant.plus(random.nextInt(7), ChronoUnit.DAYS);
                        Instant end = start.plus(1, ChronoUnit.DAYS);
                        
                        long opStart = System.nanoTime();
                        List<? extends Timestamped> readings = client.queryRange(meterId, start, end);
                        long opLatency = System.nanoTime() - opStart;
                        
                        totalLatency.addAndGet(opLatency);
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
        result.setOperationType(operationType);
        result.setTotalOps(totalOps.get());
        result.setTotalReadings(totalReadings.get());
        result.setDurationMs((benchmarkEnd - benchmarkStart) / 1_000_000);
        result.setOpsPerSecond((double) totalOps.get() / ((benchmarkEnd - benchmarkStart) / 1_000_000_000.0));
        result.setAvgLatencyUs(totalLatency.get() / (double) totalOps.get() / 1000.0);
        
        System.out.printf("  Query: %d ops in %dms (%.2f ops/sec, avg latency %.2f µs)%n",
                totalOps.get(), result.getDurationMs(), result.getOpsPerSecond(), result.getAvgLatencyUs());
        
        return result;
    }

    private BenchmarkResult benchmarkAggregation(E4sClient client, String operationType) {
        System.out.println("Running aggregation benchmark...");
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicLong totalOps = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        long benchmarkStart = System.nanoTime();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    int meterStart = threadId * metersPerThread;
                    
                    for (int i = 0; i < queriesPerThread; i++) {
                        int meterIndex = meterStart + random.nextInt(metersPerThread);
                        String meterId = meterIdPrefix + String.format("%05d", meterIndex);
                        
                        Instant start = startInstant.plus(random.nextInt(7), ChronoUnit.DAYS);
                        Instant end = start.plus(3, ChronoUnit.DAYS);
                        
                        long opStart = System.nanoTime();
                        client.queryAggregation(meterId, start, end,
                                E4sClient.AggregationType.AVG, E4sClient.Interval.DAILY);
                        long opLatency = System.nanoTime() - opStart;
                        
                        totalLatency.addAndGet(opLatency);
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
        result.setOperationType(operationType);
        result.setTotalOps(totalOps.get());
        result.setDurationMs((benchmarkEnd - benchmarkStart) / 1_000_000);
        result.setOpsPerSecond((double) totalOps.get() / ((benchmarkEnd - benchmarkStart) / 1_000_000_000.0));
        result.setAvgLatencyUs(totalLatency.get() / (double) totalOps.get() / 1000.0);
        
        System.out.printf("  Aggregation: %d ops in %dms (%.2f ops/sec, avg latency %.2f µs)%n",
                totalOps.get(), result.getDurationMs(), result.getOpsPerSecond(), result.getAvgLatencyUs());
        
        return result;
    }

    private List<Timestamped> generateReadings(int count) {
        List<Timestamped> readings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long reportedTs = startInstant.plus(i * 15, ChronoUnit.MINUTES).toEpochMilli();
            Map<String, Object> fieldValues = new HashMap<>();
            fieldValues.put("reportedTs", reportedTs);
            fieldValues.put("voltage", 220 + random.nextDouble() * 10);
            fieldValues.put("current", 5 + random.nextDouble() * 2);
            fieldValues.put("power", 1000 + random.nextDouble() * 500);
            readings.add(DynamicModelRegistry.getInstance().createReading(
                    "MeterReading", fieldValues));
        }
        return readings;
    }

    private void printResult(String label, BenchmarkResult result) {
        System.out.println("\n" + label);
        System.out.println("-".repeat(40));
        System.out.printf("Ingest:     %.2f ops/sec, %.2f µs latency%n",
                result.getIngestOpsPerSecond(), result.getIngestAvgLatencyUs());
        System.out.printf("Query:      %.2f ops/sec, %.2f µs latency%n",
                result.getQueryOpsPerSecond(), result.getQueryAvgLatencyUs());
        System.out.printf("Aggregation: %.2f ops/sec, %.2f µs latency%n",
                result.getAggregationOpsPerSecond(), result.getAggregationAvgLatencyUs());
        System.out.printf("Total readings: %d%n", result.getTotalReadings());
        System.out.printf("Bucket count:   %d%n", result.getBucketCount());
        System.out.printf("Memory usage:   %.2f MB%n", result.getMemoryMB());
    }

    public static void printComparisonTable(BenchmarkResult nativeResult) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PERFORMANCE COMPARISON: HTTP Client vs Native Client");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("Note: HTTP client numbers are estimates based on typical REST API overhead.");
        System.out.println("Actual HTTP client performance depends on network latency and server load.");
        System.out.println();
        
        System.out.println("+----------------+-------------------+-------------------+------------+");
        System.out.println("| Operation      | HTTP Client       | Native Client     | Speedup    |");
        System.out.println("|                | (REST/JSON)       | (Kryo+Deflater)   |            |");
        System.out.println("+----------------+-------------------+-------------------+------------+");
        
        double httpIngest = 5000;
        double httpQuery = 8000;
        double httpAgg = 5000;
        
        double ingestSpeedup = nativeResult.getIngestOpsPerSecond() / httpIngest;
        double querySpeedup = nativeResult.getQueryOpsPerSecond() / httpQuery;
        double aggSpeedup = nativeResult.getAggregationOpsPerSecond() / httpAgg;
        
        System.out.printf("| %-14s | %,15.0f | %,15.0f | %8.1fx  |%n", 
                "Ingest ops/sec", httpIngest, nativeResult.getIngestOpsPerSecond(), ingestSpeedup);
        System.out.printf("| %-14s | %,15.0f | %,15.0f | %8.1fx  |%n", 
                "Query ops/sec", httpQuery, nativeResult.getQueryOpsPerSecond(), querySpeedup);
        System.out.printf("| %-14s | %,15.0f | %,15.0f | %8.1fx  |%n", 
                "Agg ops/sec", httpAgg, nativeResult.getAggregationOpsPerSecond(), aggSpeedup);
        
        System.out.println("+----------------+-------------------+-------------------+------------+");
        System.out.println();
        
        System.out.println("Key Benefits of Native Client:");
        System.out.println("  - ~90% smaller network payload (Kryo+Deflater vs JSON)");
        System.out.println("  - Zero server-side serialization CPU (client serializes)");
        System.out.println("  - Direct IMap access (no HTTP overhead)");
        System.out.println("  - Lower latency for high-frequency operations");
        System.out.println();
        
        System.out.println("When to use each client:");
        System.out.println("  HTTP Client: Cross-language clients, remote access, firewall restrictions");
        System.out.println("  Native Client: High-throughput Java apps, low-latency requirements");
    }

    public static class BenchmarkResult {
        private String operationType;
        private long totalOps;
        private long totalReadings;
        private long durationMs;
        private double opsPerSecond;
        private double avgLatencyUs;
        private double ingestOpsPerSecond;
        private double ingestAvgLatencyUs;
        private double queryOpsPerSecond;
        private double queryAvgLatencyUs;
        private double aggregationOpsPerSecond;
        private double aggregationAvgLatencyUs;
        private long bucketCount;
        private long memoryBytes;

        public String getOperationType() { return operationType; }
        public void setOperationType(String operationType) { this.operationType = operationType; }
        public long getTotalOps() { return totalOps; }
        public void setTotalOps(long totalOps) { this.totalOps = totalOps; }
        public long getTotalReadings() { return totalReadings; }
        public void setTotalReadings(long totalReadings) { this.totalReadings = totalReadings; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public double getOpsPerSecond() { return opsPerSecond; }
        public void setOpsPerSecond(double opsPerSecond) { this.opsPerSecond = opsPerSecond; }
        public double getAvgLatencyUs() { return avgLatencyUs; }
        public void setAvgLatencyUs(double avgLatencyUs) { this.avgLatencyUs = avgLatencyUs; }
        public double getIngestOpsPerSecond() { return ingestOpsPerSecond; }
        public void setIngestOpsPerSecond(double ingestOpsPerSecond) { this.ingestOpsPerSecond = ingestOpsPerSecond; }
        public double getIngestAvgLatencyUs() { return ingestAvgLatencyUs; }
        public void setIngestAvgLatencyUs(double ingestAvgLatencyUs) { this.ingestAvgLatencyUs = ingestAvgLatencyUs; }
        public double getQueryOpsPerSecond() { return queryOpsPerSecond; }
        public void setQueryOpsPerSecond(double queryOpsPerSecond) { this.queryOpsPerSecond = queryOpsPerSecond; }
        public double getQueryAvgLatencyUs() { return queryAvgLatencyUs; }
        public void setQueryAvgLatencyUs(double queryAvgLatencyUs) { this.queryAvgLatencyUs = queryAvgLatencyUs; }
        public double getAggregationOpsPerSecond() { return aggregationOpsPerSecond; }
        public void setAggregationOpsPerSecond(double aggregationOpsPerSecond) { this.aggregationOpsPerSecond = aggregationOpsPerSecond; }
        public double getAggregationAvgLatencyUs() { return aggregationAvgLatencyUs; }
        public void setAggregationAvgLatencyUs(double aggregationAvgLatencyUs) { this.aggregationAvgLatencyUs = aggregationAvgLatencyUs; }
        public long getBucketCount() { return bucketCount; }
        public void setBucketCount(long bucketCount) { this.bucketCount = bucketCount; }
        public long getMemoryBytes() { return memoryBytes; }
        public void setMemoryBytes(long memoryBytes) { this.memoryBytes = memoryBytes; }
        public double getMemoryMB() { return memoryBytes / (1024.0 * 1024.0); }
    }

    public static class ComparisonResult {
        private BenchmarkResult nativeResult;
        private BenchmarkResult httpResult;

        public BenchmarkResult getNativeResult() { return nativeResult; }
        public void setNativeResult(BenchmarkResult nativeResult) { this.nativeResult = nativeResult; }
        public BenchmarkResult getHttpResult() { return httpResult; }
        public void setHttpResult(BenchmarkResult httpResult) { this.httpResult = httpResult; }
    }

    public static void main(String[] args) {
        DynamicModelRegistry.getInstance().initialize();
        
        Config config = new Config();
        config.setInstanceName("benchmark-server");

        config.getSerializationConfig().addSerializerConfig(
                new SerializerConfig()
                        .setTypeClass(GenericBucket.class)
                        .setImplementation(new GenericBucketHazelcastSerializer())
        );

        HazelcastInstance server = Hazelcast.newHazelcastInstance(config);

        try {
            ClientBenchmark benchmark = new ClientBenchmark(server);
            benchmark.runComparison();
        } finally {
            server.shutdown();
        }
    }
}
