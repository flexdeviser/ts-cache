package org.e4s.server.job;

import org.e4s.server.service.MeterCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EvictionJob {

    private static final Logger log = LoggerFactory.getLogger(EvictionJob.class);

    private final MeterCacheService meterCacheService;

    @Value("${e4s.retention-days:21}")
    private int retentionDays;

    @Value("${e4s.idle-hours:24}")
    private int idleHours;

    public EvictionJob(MeterCacheService meterCacheService) {
        this.meterCacheService = meterCacheService;
    }

    @Scheduled(fixedRateString = "${e4s.eviction.interval-ms:3600000}")
    public void evictOldBuckets() {
        log.info("Starting eviction job... Current bucket count: {}", meterCacheService.getBucketCount());
        
        long startTime = System.currentTimeMillis();
        
        meterCacheService.evictOldBuckets(retentionDays, idleHours);
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Eviction job completed in {}ms. New bucket count: {}", 
                duration, meterCacheService.getBucketCount());
    }
}
