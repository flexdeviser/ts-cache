package org.e4s.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.e4s.server.model.MeterReadingV2;
import org.e4s.server.service.MeterCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MeterCacheService meterCacheService;

    @Test
    void testQueryRange() throws Exception {
        Instant start = Instant.parse("2026-02-18T00:00:00Z");
        Instant end = Instant.parse("2026-02-18T23:59:59Z");

        List<MeterReadingV2> readings = Arrays.asList(
                new MeterReadingV2(Instant.parse("2026-02-18T10:00:00Z").toEpochMilli(), 220.5, 5.2, 1146.6),
                new MeterReadingV2(Instant.parse("2026-02-18T10:15:00Z").toEpochMilli(), 221.0, 5.3, 1171.3)
        );

        when(meterCacheService.queryRange("MTR-001", start, end)).thenReturn(readings);

        mockMvc.perform(get("/api/v1/meters/MTR-001/data")
                        .param("start", start.toString())
                        .param("end", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meterId").value("MTR-001"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.readings").isArray())
                .andExpect(jsonPath("$.readings[0].voltage").value(220.5));

        verify(meterCacheService).queryRange("MTR-001", start, end);
    }

    @Test
    void testQueryRangeEmptyResult() throws Exception {
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now();

        when(meterCacheService.queryRange(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/meters/MTR-999/data")
                        .param("start", start.toString())
                        .param("end", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meterId").value("MTR-999"))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.readings").isEmpty());
    }

    @Test
    void testQueryAggregate() throws Exception {
        Instant start = Instant.parse("2026-02-18T00:00:00Z");
        Instant end = Instant.parse("2026-02-18T23:59:59Z");

        MeterCacheService.AggregationResult mockResult = new MeterCacheService.AggregationResult();
        mockResult.setMeterId("MTR-001");
        mockResult.setAggregationType(MeterCacheService.AggregationType.AVG);
        mockResult.setInterval(MeterCacheService.Interval.HOURLY);
        mockResult.setValue(1150.5);
        mockResult.setCount(96);

        when(meterCacheService.queryAggregation(
                eq("MTR-001"), any(Instant.class), any(Instant.class),
                eq(MeterCacheService.AggregationType.AVG),
                eq(MeterCacheService.Interval.HOURLY)))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/meters/MTR-001/aggregate")
                        .param("start", start.toString())
                        .param("end", end.toString())
                        .param("type", "AVG")
                        .param("interval", "HOURLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meterId").value("MTR-001"))
                .andExpect(jsonPath("$.aggregationType").value("AVG"))
                .andExpect(jsonPath("$.value").value(1150.5))
                .andExpect(jsonPath("$.count").value(96));

        verify(meterCacheService).queryAggregation(
                eq("MTR-001"), any(Instant.class), any(Instant.class),
                eq(MeterCacheService.AggregationType.AVG),
                eq(MeterCacheService.Interval.HOURLY));
    }

    @Test
    void testQueryAggregateWithDefaults() throws Exception {
        MeterCacheService.AggregationResult mockResult = new MeterCacheService.AggregationResult();
        mockResult.setMeterId("MTR-001");
        mockResult.setAggregationType(MeterCacheService.AggregationType.AVG);
        mockResult.setInterval(MeterCacheService.Interval.HOURLY);

        when(meterCacheService.queryAggregation(
                anyString(), any(Instant.class), any(Instant.class),
                any(MeterCacheService.AggregationType.class),
                any(MeterCacheService.Interval.class)))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/meters/MTR-001/aggregate")
                        .param("start", Instant.now().minus(1, ChronoUnit.DAYS).toString())
                        .param("end", Instant.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meterId").value("MTR-001"));
    }

    @Test
    void testQueryAggregateSum() throws Exception {
        MeterCacheService.AggregationResult mockResult = new MeterCacheService.AggregationResult();
        mockResult.setMeterId("MTR-001");
        mockResult.setAggregationType(MeterCacheService.AggregationType.SUM);
        mockResult.setValue(110400.0);
        mockResult.setCount(96);

        when(meterCacheService.queryAggregation(
                anyString(), any(Instant.class), any(Instant.class),
                eq(MeterCacheService.AggregationType.SUM),
                any(MeterCacheService.Interval.class)))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/meters/MTR-001/aggregate")
                        .param("start", Instant.now().minus(1, ChronoUnit.DAYS).toString())
                        .param("end", Instant.now().toString())
                        .param("type", "SUM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregationType").value("SUM"))
                .andExpect(jsonPath("$.value").value(110400.0));
    }

    @Test
    void testQueryAggregateMin() throws Exception {
        MeterCacheService.AggregationResult mockResult = new MeterCacheService.AggregationResult();
        mockResult.setMeterId("MTR-001");
        mockResult.setAggregationType(MeterCacheService.AggregationType.MIN);
        mockResult.setValue(800.0);

        when(meterCacheService.queryAggregation(
                anyString(), any(Instant.class), any(Instant.class),
                eq(MeterCacheService.AggregationType.MIN),
                any(MeterCacheService.Interval.class)))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/meters/MTR-001/aggregate")
                        .param("start", Instant.now().minus(1, ChronoUnit.DAYS).toString())
                        .param("end", Instant.now().toString())
                        .param("type", "MIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregationType").value("MIN"));
    }

    @Test
    void testQueryAggregateMax() throws Exception {
        MeterCacheService.AggregationResult mockResult = new MeterCacheService.AggregationResult();
        mockResult.setMeterId("MTR-001");
        mockResult.setAggregationType(MeterCacheService.AggregationType.MAX);
        mockResult.setValue(1500.0);

        when(meterCacheService.queryAggregation(
                anyString(), any(Instant.class), any(Instant.class),
                eq(MeterCacheService.AggregationType.MAX),
                any(MeterCacheService.Interval.class)))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/meters/MTR-001/aggregate")
                        .param("start", Instant.now().minus(1, ChronoUnit.DAYS).toString())
                        .param("end", Instant.now().toString())
                        .param("type", "MAX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregationType").value("MAX"));
    }

    @Test
    void testQueryAggregateCount() throws Exception {
        MeterCacheService.AggregationResult mockResult = new MeterCacheService.AggregationResult();
        mockResult.setMeterId("MTR-001");
        mockResult.setAggregationType(MeterCacheService.AggregationType.COUNT);
        mockResult.setValue(96.0);
        mockResult.setCount(96);

        when(meterCacheService.queryAggregation(
                anyString(), any(Instant.class), any(Instant.class),
                eq(MeterCacheService.AggregationType.COUNT),
                any(MeterCacheService.Interval.class)))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/v1/meters/MTR-001/aggregate")
                        .param("start", Instant.now().minus(1, ChronoUnit.DAYS).toString())
                        .param("end", Instant.now().toString())
                        .param("type", "COUNT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregationType").value("COUNT"))
                .andExpect(jsonPath("$.value").value(96.0));
    }
}
