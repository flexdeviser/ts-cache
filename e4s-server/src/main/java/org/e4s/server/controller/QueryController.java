package org.e4s.server.controller;

import org.e4s.server.model.MeterReadingV2;
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
        List<MeterReadingV2> readings = meterCacheService.queryRange(meterId, start, end);
        return ResponseEntity.ok(new QueryResponse(meterId, readings.size(), readings));
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
        private List<MeterReadingV2> readings;

        public QueryResponse(String meterId, int count, List<MeterReadingV2> readings) {
            this.meterId = meterId;
            this.count = count;
            this.readings = readings;
        }

        public String getMeterId() {
            return meterId;
        }

        public void setMeterId(String meterId) {
            this.meterId = meterId;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public List<MeterReadingV2> getReadings() {
            return readings;
        }

        public void setReadings(List<MeterReadingV2> readings) {
            this.readings = readings;
        }
    }
}
