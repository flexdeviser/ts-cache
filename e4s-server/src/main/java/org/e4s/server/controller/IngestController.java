package org.e4s.server.controller;

import org.e4s.model.MeterReading;
import org.e4s.server.service.MeterCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class IngestController {

    private final MeterCacheService meterCacheService;

    public IngestController(MeterCacheService meterCacheService) {
        this.meterCacheService = meterCacheService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingestSingle(
            @RequestParam String meterId,
            @RequestBody MeterReading reading) {
        meterCacheService.ingestReading(meterId, reading);
        return ResponseEntity.ok(new IngestResponse("success", 1));
    }

    @PostMapping("/ingest/batch")
    public ResponseEntity<IngestResponse> ingestBatch(
            @RequestParam String meterId,
            @RequestBody List<MeterReading> readings) {
        meterCacheService.ingestReadings(meterId, readings);
        return ResponseEntity.ok(new IngestResponse("success", readings.size()));
    }

    @PostMapping("/batch")
    public ResponseEntity<IngestResponse> batchIngest(
            @RequestBody List<MeterCacheService.IngestRequest> requests) {
        meterCacheService.ingestBatch(requests);
        int totalCount = requests.stream()
                .mapToInt(r -> r.getReadings().size())
                .sum();
        return ResponseEntity.ok(new IngestResponse("success", totalCount));
    }

    public static class IngestResponse {
        private String status;
        private int count;

        public IngestResponse(String status, int count) {
            this.status = status;
            this.count = count;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
