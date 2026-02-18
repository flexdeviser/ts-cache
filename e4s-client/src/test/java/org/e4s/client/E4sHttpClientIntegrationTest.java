package org.e4s.client;

import org.e4s.client.http.E4sHttpClient;
import org.e4s.model.MeterReading;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class E4sHttpClientIntegrationTest {

    private static final String SERVER_URL = "http://localhost:8080";
    private static final Random random = new Random();

    private E4sClient client;

    @BeforeEach
    void setUp() {
        client = new E4sHttpClient(SERVER_URL);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                System.err.println("Error closing client: " + e.getMessage());
            }
        }
    }

    @Test
    void testHealthCheck() {
        boolean healthy = client.isHealthy();
        System.out.println("Server healthy: " + healthy);
        assertTrue(healthy, "Server should be healthy");
    }

    @Test
    void testIngestSingleReading() {
        MeterReading reading = createReading(Instant.now());
        
        assertDoesNotThrow(() -> client.ingestReading("TEST-MTR-001", reading));
        System.out.println("Single reading ingested successfully");
    }

    @Test
    void testIngestBatchReadings() {
        List<MeterReading> readings = createReadings(96, Instant.now().minus(1, ChronoUnit.DAYS));
        
        assertDoesNotThrow(() -> client.ingestReadings("TEST-MTR-002", readings));
        System.out.println("Batch of " + readings.size() + " readings ingested successfully");
    }

    @Test
    void testMultiMeterBatchIngest() {
        List<E4sClient.IngestRequest> requests = new ArrayList<>();
        
        for (int m = 1; m <= 10; m++) {
            String meterId = "TEST-MTR-" + String.format("%03d", m);
            List<MeterReading> readings = createReadings(96, Instant.now().minus(1, ChronoUnit.DAYS));
            requests.add(new E4sClient.IngestRequest(meterId, readings));
        }
        
        assertDoesNotThrow(() -> client.ingestBatch(requests));
        System.out.println("Multi-meter batch ingest completed for " + requests.size() + " meters");
    }

    @Test
    void testQueryRange() {
        String meterId = "TEST-MTR-QUERY-001";
        Instant baseTime = Instant.now().minus(7, ChronoUnit.DAYS);
        
        List<MeterReading> readings = createReadings(96 * 7, baseTime);
        client.ingestReadings(meterId, readings);
        
        Instant start = baseTime.plus(1, ChronoUnit.DAYS);
        Instant end = baseTime.plus(3, ChronoUnit.DAYS);
        
        List<MeterReading> result = client.queryRange(meterId, start, end);
        
        System.out.println("Query returned " + result.size() + " readings");
        assertFalse(result.isEmpty(), "Should return readings");
    }

    @Test
    void testAggregationAvg() {
        String meterId = "TEST-MTR-AGG-001";
        Instant baseTime = Instant.now().minus(3, ChronoUnit.DAYS);
        
        List<MeterReading> readings = createReadings(96 * 3, baseTime);
        client.ingestReadings(meterId, readings);
        
        Instant start = baseTime;
        Instant end = baseTime.plus(3, ChronoUnit.DAYS);
        
        E4sClient.AggregationResult result = client.queryAggregation(
                meterId, start, end,
                E4sClient.AggregationType.AVG,
                E4sClient.Interval.DAILY);
        
        System.out.println("Avg power: " + result.getValue() + " W");
        assertNotNull(result.getValue(), "Should have avg value");
        assertTrue(result.getCount() > 0, "Should have count > 0");
    }

    @Test
    void testAggregationSum() {
        String meterId = "TEST-MTR-AGG-002";
        Instant baseTime = Instant.now().minus(3, ChronoUnit.DAYS);
        
        List<MeterReading> readings = createReadings(96 * 3, baseTime);
        client.ingestReadings(meterId, readings);
        
        Instant start = baseTime;
        Instant end = baseTime.plus(3, ChronoUnit.DAYS);
        
        E4sClient.AggregationResult result = client.queryAggregation(
                meterId, start, end,
                E4sClient.AggregationType.SUM,
                E4sClient.Interval.DAILY);
        
        System.out.println("Sum power: " + result.getValue() + " W");
        assertNotNull(result.getValue(), "Should have sum value");
    }

    @Test
    void testAggregationMinMax() {
        String meterId = "TEST-MTR-AGG-003";
        Instant baseTime = Instant.now().minus(1, ChronoUnit.DAYS);
        
        List<MeterReading> readings = createReadings(96, baseTime);
        client.ingestReadings(meterId, readings);
        
        Instant start = baseTime;
        Instant end = baseTime.plus(1, ChronoUnit.DAYS);
        
        E4sClient.AggregationResult minResult = client.queryAggregation(
                meterId, start, end,
                E4sClient.AggregationType.MIN,
                E4sClient.Interval.HOURLY);
        
        E4sClient.AggregationResult maxResult = client.queryAggregation(
                meterId, start, end,
                E4sClient.AggregationType.MAX,
                E4sClient.Interval.HOURLY);
        
        System.out.println("Min power: " + minResult.getValue() + " W");
        System.out.println("Max power: " + maxResult.getValue() + " W");
        assertTrue(maxResult.getValue() >= minResult.getValue(), "Max should be >= Min");
    }

    @Test
    void testCacheStats() {
        E4sClient.CacheStats stats = client.getCacheStats();
        
        System.out.println("=== Cache Stats ===");
        System.out.println("Total Entries: " + stats.getTotalEntries());
        System.out.println("Memory (MB): " + stats.getMemoryMB());
        System.out.println("Memory (GB): " + stats.getMemoryGB());
        System.out.println("Put Count: " + stats.getPutCount());
        System.out.println("Get Count: " + stats.getGetCount());
        
        assertNotNull(stats, "Should return stats");
    }

    @Test
    void testFullWorkflow() {
        String meterId = "TEST-MTR-FULL-001";
        Instant baseTime = Instant.now().minus(1, ChronoUnit.DAYS);
        
        System.out.println("=== Full Workflow Test ===");
        
        System.out.println("\n1. Health Check");
        boolean healthy = client.isHealthy();
        System.out.println("   Server healthy: " + healthy);
        
        System.out.println("\n2. Ingest Data");
        List<MeterReading> readings = createReadings(96, baseTime);
        client.ingestReadings(meterId, readings);
        System.out.println("   Ingested " + readings.size() + " readings");
        
        System.out.println("\n3. Query Range");
        List<MeterReading> queried = client.queryRange(meterId, baseTime, baseTime.plus(1, ChronoUnit.DAYS));
        System.out.println("   Queried " + queried.size() + " readings");
        
        System.out.println("\n4. Aggregation");
        E4sClient.AggregationResult avgResult = client.queryAggregation(
                meterId, baseTime, baseTime.plus(1, ChronoUnit.DAYS),
                E4sClient.AggregationType.AVG,
                E4sClient.Interval.HOURLY);
        System.out.println("   Avg power: " + String.format("%.2f", avgResult.getValue()) + " W");
        
        System.out.println("\n5. Cache Stats");
        E4sClient.CacheStats stats = client.getCacheStats();
        System.out.println("   Total buckets: " + stats.getTotalEntries());
        System.out.println("   Memory usage: " + String.format("%.2f MB", stats.getMemoryMB()));
        
        System.out.println("\n=== Workflow Complete ===");
    }

    private MeterReading createReading(Instant timestamp) {
        return new MeterReading(
                timestamp.toEpochMilli(),
                220.0 + random.nextDouble() * 10,
                5.0 + random.nextDouble() * 2,
                1000.0 + random.nextDouble() * 500
        );
    }

    private List<MeterReading> createReadings(int count, Instant startTime) {
        List<MeterReading> readings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Instant timestamp = startTime.plus(i * 15, ChronoUnit.MINUTES);
            readings.add(createReading(timestamp));
        }
        return readings;
    }
}
