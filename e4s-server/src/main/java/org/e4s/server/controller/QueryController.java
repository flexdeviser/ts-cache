package org.e4s.server.controller;

import org.e4s.model.Timestamped;
import org.e4s.model.dynamic.DynamicModelRegistry;
import org.e4s.server.service.MeterCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/meters")
public class QueryController {

    private final MeterCacheService meterCacheService;

    public QueryController(MeterCacheService meterCacheService) {
        this.meterCacheService = meterCacheService;
    }

    @GetMapping("/{meterId}/data")
    public ResponseEntity<QueryResponse> queryRange(
            @PathVariable String meterId,
            @RequestParam Instant start,
            @RequestParam Instant end) {
        List<Timestamped> readings = meterCacheService.queryRange(meterId, start, end);
        DynamicModelRegistry registry = DynamicModelRegistry.getInstance();
        List<QueryResponse.Reading> readingResponses = readings.stream()
                .map(r -> new QueryResponse.Reading(
                        r.getTimestamp(),
                        (Double) registry.getFieldValue(r, "voltage"),
                        (Double) registry.getFieldValue(r, "current"),
                        (Double) registry.getFieldValue(r, "power")))
                .toList();
        return ResponseEntity.ok(new QueryResponse(meterId, readingResponses.size(), readingResponses));
    }

    @GetMapping("/{meterId}/aggregate")
    public ResponseEntity<MeterCacheService.AggregationResult> queryAggregate(
            @PathVariable String meterId,
            @RequestParam Instant start,
            @RequestParam Instant end,
            @RequestParam(defaultValue = "AVG") MeterCacheService.AggregationType type,
            @RequestParam(defaultValue = "HOURLY") MeterCacheService.Interval interval) {
        MeterCacheService.AggregationResult result = meterCacheService.queryAggregation(
                meterId, start, end, type, interval);
        return ResponseEntity.ok(result);
    }

    public static class QueryResponse {
        private String meterId;
        private int count;
        private List<Reading> readings;

        public QueryResponse(String meterId, int count, List<Reading> readings) {
            this.meterId = meterId;
            this.count = count;
            this.readings = readings;
        }

        public String getMeterId() { return meterId; }
        public void setMeterId(String meterId) { this.meterId = meterId; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public List<Reading> getReadings() { return readings; }
        public void setReadings(List<Reading> readings) { this.readings = readings; }

        public static class Reading {
            private long reportedTs;
            private double voltage;
            private double current;
            private double power;

            public Reading(long reportedTs, double voltage, double current, double power) {
                this.reportedTs = reportedTs;
                this.voltage = voltage;
                this.current = current;
                this.power = power;
            }

            public long getReportedTs() { return reportedTs; }
            public void setReportedTs(long reportedTs) { this.reportedTs = reportedTs; }
            public double getVoltage() { return voltage; }
            public void setVoltage(double voltage) { this.voltage = voltage; }
            public double getCurrent() { return current; }
            public void setCurrent(double current) { this.current = current; }
            public double getPower() { return power; }
            public void setPower(double power) { this.power = power; }
        }
    }
}
