package org.e4s.server.controller;

import org.e4s.server.service.MeterCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cache")
public class CacheStatsController {

    private final MeterCacheService meterCacheService;

    public CacheStatsController(MeterCacheService meterCacheService) {
        this.meterCacheService = meterCacheService;
    }

    @GetMapping("/stats")
    public ResponseEntity<CacheStatsResponse> getCacheStats() {
        MeterCacheService.CacheStats stats = meterCacheService.getCacheStats();
        
        CacheStatsResponse response = new CacheStatsResponse();
        response.setTotalEntries(stats.getTotalEntries());
        response.setOwnedEntries(stats.getOwnedEntries());
        response.setMemoryBytes(stats.getMemoryCostBytes());
        response.setMemoryMB(stats.getMemoryCostMB());
        response.setMemoryGB(stats.getMemoryCostGB());
        response.setHeapCostBytes(stats.getHeapCostBytes());
        response.setPutCount(stats.getPutCount());
        response.setGetCount(stats.getGetCount());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/memory")
    public ResponseEntity<MemoryResponse> getMemoryUsage() {
        long bytes = meterCacheService.getMemoryUsageBytes();
        long buckets = meterCacheService.getBucketCount();
        
        MemoryResponse response = new MemoryResponse();
        response.setBucketCount(buckets);
        response.setMemoryBytes(bytes);
        response.setMemoryMB(bytes / (1024.0 * 1024.0));
        response.setMemoryGB(bytes / (1024.0 * 1024.0 * 1024.0));
        
        if (buckets > 0) {
            response.setAvgBytesPerBucket(bytes / buckets);
        }
        
        return ResponseEntity.ok(response);
    }

    public static class CacheStatsResponse {
        private long totalEntries;
        private long ownedEntries;
        private long memoryBytes;
        private double memoryMB;
        private double memoryGB;
        private long heapCostBytes;
        private long putCount;
        private long getCount;

        public long getTotalEntries() {
            return totalEntries;
        }

        public void setTotalEntries(long totalEntries) {
            this.totalEntries = totalEntries;
        }

        public long getOwnedEntries() {
            return ownedEntries;
        }

        public void setOwnedEntries(long ownedEntries) {
            this.ownedEntries = ownedEntries;
        }

        public long getMemoryBytes() {
            return memoryBytes;
        }

        public void setMemoryBytes(long memoryBytes) {
            this.memoryBytes = memoryBytes;
        }

        public double getMemoryMB() {
            return memoryMB;
        }

        public void setMemoryMB(double memoryMB) {
            this.memoryMB = memoryMB;
        }

        public double getMemoryGB() {
            return memoryGB;
        }

        public void setMemoryGB(double memoryGB) {
            this.memoryGB = memoryGB;
        }

        public long getHeapCostBytes() {
            return heapCostBytes;
        }

        public void setHeapCostBytes(long heapCostBytes) {
            this.heapCostBytes = heapCostBytes;
        }

        public long getPutCount() {
            return putCount;
        }

        public void setPutCount(long putCount) {
            this.putCount = putCount;
        }

        public long getGetCount() {
            return getCount;
        }

        public void setGetCount(long getCount) {
            this.getCount = getCount;
        }
    }

    public static class MemoryResponse {
        private long bucketCount;
        private long memoryBytes;
        private double memoryMB;
        private double memoryGB;
        private long avgBytesPerBucket;

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
            return memoryMB;
        }

        public void setMemoryMB(double memoryMB) {
            this.memoryMB = memoryMB;
        }

        public double getMemoryGB() {
            return memoryGB;
        }

        public void setMemoryGB(double memoryGB) {
            this.memoryGB = memoryGB;
        }

        public long getAvgBytesPerBucket() {
            return avgBytesPerBucket;
        }

        public void setAvgBytesPerBucket(long avgBytesPerBucket) {
            this.avgBytesPerBucket = avgBytesPerBucket;
        }
    }
}
