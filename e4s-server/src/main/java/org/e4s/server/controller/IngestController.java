package org.e4s.server.controller;

import org.e4s.model.Timestamped;
import org.e4s.model.dynamic.DynamicModelRegistry;
import org.e4s.server.service.MeterCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class IngestController {

    private static final String MODEL_NAME = "MeterReading";

    private final MeterCacheService meterCacheService;

    public IngestController(MeterCacheService meterCacheService) {
        this.meterCacheService = meterCacheService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingestSingle(
            @RequestParam String meterId,
            @RequestBody ReadingRequest request) {
        Timestamped reading = createReading(request);
        meterCacheService.ingestReading(meterId, reading);
        return ResponseEntity.ok(new IngestResponse("success", 1));
    }

    @PostMapping("/ingest/batch")
    public ResponseEntity<IngestResponse> ingestBatch(
            @RequestParam String meterId,
            @RequestBody List<ReadingRequest> requests) {
        List<Timestamped> readings = requests.stream()
                .map(this::createReading)
                .toList();
        meterCacheService.ingestReadings(meterId, readings);
        return ResponseEntity.ok(new IngestResponse("success", readings.size()));
    }

    @PostMapping("/batch")
    public ResponseEntity<IngestResponse> batchIngest(
            @RequestBody List<BatchRequest> requests) {
        List<MeterCacheService.IngestRequest> ingestRequests = requests.stream()
                .map(req -> {
                    MeterCacheService.IngestRequest ir = new MeterCacheService.IngestRequest();
                    ir.setMeterId(req.getMeterId());
                    ir.setReadings(req.getReadings().stream()
                            .map(this::createReading)
                            .toList());
                    return ir;
                })
                .toList();
        meterCacheService.ingestBatch(ingestRequests);
        int totalCount = ingestRequests.stream()
                .mapToInt(r -> r.getReadings().size())
                .sum();
        return ResponseEntity.ok(new IngestResponse("success", totalCount));
    }

    private Timestamped createReading(ReadingRequest request) {
        DynamicModelRegistry registry = DynamicModelRegistry.getInstance();
        Map<String, Object> fieldValues = new HashMap<>();
        fieldValues.put("reportedTs", request.getReportedTs());
        fieldValues.put("voltage", request.getVoltage());
        fieldValues.put("current", request.getCurrent());
        fieldValues.put("power", request.getPower());
        return registry.createReading(MODEL_NAME, fieldValues);
    }

    public static class ReadingRequest {
        private long reportedTs;
        private double voltage;
        private double current;
        private double power;

        public long getReportedTs() { return reportedTs; }
        public void setReportedTs(long reportedTs) { this.reportedTs = reportedTs; }
        public double getVoltage() { return voltage; }
        public void setVoltage(double voltage) { this.voltage = voltage; }
        public double getCurrent() { return current; }
        public void setCurrent(double current) { this.current = current; }
        public double getPower() { return power; }
        public void setPower(double power) { this.power = power; }
    }

    public static class BatchRequest {
        private String meterId;
        private List<ReadingRequest> readings;

        public String getMeterId() { return meterId; }
        public void setMeterId(String meterId) { this.meterId = meterId; }
        public List<ReadingRequest> getReadings() { return readings; }
        public void setReadings(List<ReadingRequest> readings) { this.readings = readings; }
    }

    public static class IngestResponse {
        private String status;
        private int count;

        public IngestResponse(String status, int count) {
            this.status = status;
            this.count = count;
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
}
