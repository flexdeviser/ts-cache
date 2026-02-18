package org.e4s.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.e4s.model.MeterReading;
import org.e4s.server.service.MeterCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestController.class)
class IngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MeterCacheService meterCacheService;

    @Test
    void testIngestSingle() throws Exception {
        long now = System.currentTimeMillis();
        MeterReading reading = new MeterReading(now, 220.5, 5.2, 1146.6);

        mockMvc.perform(post("/api/v1/ingest")
                        .param("meterId", "MTR-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reading)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.count").value(1));

        verify(meterCacheService).ingestReading(eq("MTR-001"), any(MeterReading.class));
    }

    @Test
    void testIngestBatch() throws Exception {
        long now = System.currentTimeMillis();
        List<MeterReading> readings = Arrays.asList(
                new MeterReading(now, 1.0, 1.0, 1.0),
                new MeterReading(now + 900000, 1.0, 1.0, 1.0)
        );

        mockMvc.perform(post("/api/v1/ingest/batch")
                        .param("meterId", "MTR-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(readings)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.count").value(2));

        verify(meterCacheService).ingestReadings(eq("MTR-001"), anyList());
    }

    @Test
    void testBatchIngest() throws Exception {
        MeterCacheService.IngestRequest request1 = new MeterCacheService.IngestRequest();
        request1.setMeterId("MTR-001");
        request1.setReadings(Arrays.asList(
                new MeterReading(System.currentTimeMillis(), 1.0, 1.0, 1.0)
        ));

        MeterCacheService.IngestRequest request2 = new MeterCacheService.IngestRequest();
        request2.setMeterId("MTR-002");
        request2.setReadings(Arrays.asList(
                new MeterReading(System.currentTimeMillis(), 1.0, 1.0, 1.0),
                new MeterReading(System.currentTimeMillis() + 900000, 1.0, 1.0, 1.0)
        ));

        List<MeterCacheService.IngestRequest> requests = Arrays.asList(request1, request2);

        mockMvc.perform(post("/api/v1/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.count").value(3));

        verify(meterCacheService).ingestBatch(anyList());
    }

    @Test
    void testIngestSingleWithMissingMeterId() throws Exception {
        MeterReading reading = new MeterReading(System.currentTimeMillis(), 1.0, 1.0, 1.0);

        mockMvc.perform(post("/api/v1/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reading)))
                .andExpect(status().isBadRequest());

        verify(meterCacheService, never()).ingestReading(anyString(), any());
    }
}
