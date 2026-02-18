package org.e4s.server.job;

import org.e4s.server.service.MeterCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvictionJobTest {

    @Mock
    private MeterCacheService meterCacheService;

    private EvictionJob evictionJob;

    @BeforeEach
    void setUp() {
        evictionJob = new EvictionJob(meterCacheService);
        ReflectionTestUtils.setField(evictionJob, "retentionDays", 21);
        ReflectionTestUtils.setField(evictionJob, "idleHours", 24);
    }

    @Test
    void testEvictOldBucketsCalled() {
        when(meterCacheService.getBucketCount()).thenReturn(100L);

        evictionJob.evictOldBuckets();

        verify(meterCacheService).evictOldBuckets(21, 24);
        verify(meterCacheService, times(2)).getBucketCount();
    }

    @Test
    void testEvictionWithCustomRetention() {
        ReflectionTestUtils.setField(evictionJob, "retentionDays", 30);
        ReflectionTestUtils.setField(evictionJob, "idleHours", 48);
        when(meterCacheService.getBucketCount()).thenReturn(50L);

        evictionJob.evictOldBuckets();

        verify(meterCacheService).evictOldBuckets(30, 48);
    }

    @Test
    void testEvictionWithEmptyCache() {
        when(meterCacheService.getBucketCount()).thenReturn(0L);

        evictionJob.evictOldBuckets();

        verify(meterCacheService).evictOldBuckets(21, 24);
    }
}
