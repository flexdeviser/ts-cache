package org.e4s.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.e4s.model.Timestamped;
import org.e4s.model.dynamic.DynamicModelRegistry;
import org.e4s.server.service.MeterCacheService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @BeforeAll
    static void setUp() {
        DynamicModelRegistry.getInstance().initialize();
    }

    @Test
    void testIngestSingle() throws Exception {
        long now = System.currentTimeMillis();
        Map<String, Object> fieldValues = new HashMap<>();
        fieldValues.put("reportedTs", now);
        fieldValues.put("voltage", 220.5);
        fieldValues.put("current", 5.2);
        fieldValues.put("power", 1146.6);
        Timestamped reading = DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues);

        mockMvc.perform(post("/api/v1/ingest")
                        .param("meterId", "MTR-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reading)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.count").value(1));

        verify(meterCacheService).ingestReading(eq("MTR-001"), any(Timestamped.class));
    }

    @Test
    void testIngestBatch() throws Exception {
        long now = System.currentTimeMillis();
        Map<String, Object> fv1 = new HashMap<>();
        fv1.put("reportedTs", now);
        fv1.put("voltage", 1.0);
        fv1.put("current", 1.0);
        fv1.put("power", 1.0);
        Map<String, Object> fv2 = new HashMap<>();
        fv2.put("reportedTs", now + 900000);
        fv2.put("voltage", 1.0);
        fv2.put("current", 1.0);
        fv2.put("power", 1.0);
        List<Timestamped> readings = Arrays.asList(
                DynamicModelRegistry.getInstance().createReading("MeterReading", fv1),
                DynamicModelRegistry.getInstance().createReading("MeterReading", fv2)
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
        Map<String, Object> rv1 = new HashMap<>();
        rv1.put("reportedTs", System.currentTimeMillis());
        rv1.put("voltage", 1.0);
        rv1.put("current", 1.0);
        rv1.put("power", 1.0);
        request1.setReadings(Arrays.asList(
                DynamicModelRegistry.getInstance().createReading("MeterReading", rv1)
        ));

        MeterCacheService.IngestRequest request2 = new MeterCacheService.IngestRequest();
        request2.setMeterId("MTR-002");
        Map<String, Object> rv2 = new HashMap<>();
        rv2.put("reportedTs", System.currentTimeMillis());
        rv2.put("voltage", 1.0);
        rv2.put("current", 1.0);
        rv2.put("power", 1.0);
        Map<String, Object> rv3 = new HashMap<>();
        rv3.put("reportedTs", System.currentTimeMillis() + 900000);
        rv3.put("voltage", 1.0);
        rv3.put("current", 1.0);
        rv3.put("power", 1.0);
        request2.setReadings(Arrays.asList(
                DynamicModelRegistry.getInstance().createReading("MeterReading", rv2),
                DynamicModelRegistry.getInstance().createReading("MeterReading", rv3)
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
        Map<String, Object> fieldValues = new HashMap<>();
        fieldValues.put("reportedTs", System.currentTimeMillis());
        fieldValues.put("voltage", 1.0);
        fieldValues.put("current", 1.0);
        fieldValues.put("power", 1.0);
        Timestamped reading = DynamicModelRegistry.getInstance().createReading("MeterReading", fieldValues);

        mockMvc.perform(post("/api/v1/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reading)))
                .andExpect(status().isBadRequest());

        verify(meterCacheService, never()).ingestReading(anyString(), any());
    }
}
