package org.e4s.client.example;

import org.e4s.client.AggregationResult;
import org.e4s.client.AggregationType;
import org.e4s.client.CacheStats;
import org.e4s.client.E4sClient;
import org.e4s.client.IngestRequest;
import org.e4s.client.Interval;
import org.e4s.client.hazelcast.E4sHzClient;
import org.e4s.model.Timestamped;
import org.e4s.model.dynamic.DynamicModelRegistry;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ExampleClient {

    private static final Random random = new Random();

    public static void main(String[] args) {
        String address = System.getenv().getOrDefault("E4S_ADDRESS", "localhost:5701");
        String modelsPath = System.getenv().getOrDefault("E4S_MODELS_PATH", null);
        
        System.out.println("==============================================");
        System.out.println("E4S Example Client");
        System.out.println("==============================================");
        System.out.println("  Address: " + address);
        System.out.println("  Models: " + (modelsPath != null ? modelsPath : "classpath:models.xml"));
        System.out.println("==============================================");
        System.out.println();
        
        try (E4sClient client = new E4sHzClient(address, modelsPath)) {
            System.out.println("Connected to server successfully!");
            System.out.println();
            
            runDemo(client);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void runDemo(E4sClient client) {
        String meterId = "DEMO-MTR-001";
        Instant baseTime = Instant.now().minus(1, ChronoUnit.DAYS);
        
        System.out.println("--- Ingest Demo ---");
        List<Timestamped> readings = generateReadings(96, baseTime);
        client.ingestReadings(meterId, readings);
        System.out.println("Ingested " + readings.size() + " readings for " + meterId);
        System.out.println();
        
        System.out.println("--- Query Demo ---");
        Instant start = baseTime;
        Instant end = baseTime.plus(1, ChronoUnit.DAYS);
        List<Timestamped> queried = client.queryRange(meterId, start, end);
        System.out.println("Queried " + queried.size() + " readings");
        System.out.println();
        
        System.out.println("--- Aggregation Demo ---");
        AggregationResult avgResult = client.queryAggregation(
                meterId, start, end, AggregationType.AVG, Interval.HOURLY);
        System.out.println("Avg power: " + String.format("%.2f", avgResult.getValue()) + " W");
        
        AggregationResult sumResult = client.queryAggregation(
                meterId, start, end, AggregationType.SUM, Interval.DAILY);
        System.out.println("Sum power: " + String.format("%.2f", sumResult.getValue()) + " W");
        System.out.println();
        
        System.out.println("--- Cache Stats ---");
        CacheStats stats = client.getCacheStats();
        System.out.println("Total buckets: " + stats.getTotalEntries());
        System.out.println("Memory: " + String.format("%.2f MB", stats.getMemoryMB()));
        System.out.println();
        
        System.out.println("Demo completed successfully!");
    }
    
    private static List<Timestamped> generateReadings(int count, Instant startTime) {
        List<Timestamped> readings = new ArrayList<>(count);
        
        for (int i = 0; i < count; i++) {
            Instant timestamp = startTime.plus(i * 15, ChronoUnit.MINUTES);
            
            Map<String, Object> fieldValues = new HashMap<>();
            fieldValues.put("reportedTs", timestamp.toEpochMilli());
            fieldValues.put("voltage", 220 + random.nextDouble() * 10);
            fieldValues.put("current", 5 + random.nextDouble() * 2);
            fieldValues.put("power", 1000 + random.nextDouble() * 500);
            
            Timestamped reading = DynamicModelRegistry.getInstance()
                    .createReading("MeterReading", fieldValues);
            readings.add(reading);
        }
        
        return readings;
    }
}
