package org.e4s.server.controller;

import org.e4s.server.benchmark.BenchmarkRunner;
import org.e4s.server.benchmark.BenchmarkRunner.BenchmarkConfig;
import org.e4s.server.benchmark.BenchmarkRunner.BenchmarkResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/benchmark")
public class BenchmarkController {

    private final BenchmarkRunner benchmarkRunner;

    public BenchmarkController(BenchmarkRunner benchmarkRunner) {
        this.benchmarkRunner = benchmarkRunner;
    }

    @PostMapping("/ingest")
    public ResponseEntity<BenchmarkResult> runIngestBenchmark(@RequestBody(required = false) BenchmarkConfig config) {
        if (config == null) {
            config = new BenchmarkConfig();
        }
        return ResponseEntity.ok(benchmarkRunner.runIngestBenchmark(config));
    }

    @PostMapping("/batch-ingest")
    public ResponseEntity<BenchmarkResult> runBatchIngestBenchmark(@RequestBody(required = false) BenchmarkConfig config) {
        if (config == null) {
            config = new BenchmarkConfig();
        }
        return ResponseEntity.ok(benchmarkRunner.runBatchIngestBenchmark(config));
    }

    @PostMapping("/query")
    public ResponseEntity<BenchmarkResult> runQueryBenchmark(@RequestBody(required = false) BenchmarkConfig config) {
        if (config == null) {
            config = new BenchmarkConfig();
        }
        return ResponseEntity.ok(benchmarkRunner.runQueryBenchmark(config));
    }

    @PostMapping("/aggregation")
    public ResponseEntity<BenchmarkResult> runAggregationBenchmark(@RequestBody(required = false) BenchmarkConfig config) {
        if (config == null) {
            config = new BenchmarkConfig();
        }
        return ResponseEntity.ok(benchmarkRunner.runAggregationBenchmark(config));
    }

    @PostMapping("/full")
    public ResponseEntity<List<BenchmarkResult>> runFullBenchmark(@RequestBody(required = false) BenchmarkConfig config) {
        if (config == null) {
            config = new BenchmarkConfig();
        }
        
        List<BenchmarkResult> results = new ArrayList<>();
        
        BenchmarkResult batchIngest = benchmarkRunner.runBatchIngestBenchmark(config);
        results.add(batchIngest);
        
        BenchmarkResult query = benchmarkRunner.runQueryBenchmark(config);
        results.add(query);
        
        BenchmarkResult aggregation = benchmarkRunner.runAggregationBenchmark(config);
        results.add(aggregation);
        
        return ResponseEntity.ok(results);
    }

    @GetMapping("/config")
    public ResponseEntity<BenchmarkConfig> getDefaultConfig() {
        return ResponseEntity.ok(new BenchmarkConfig());
    }
}
