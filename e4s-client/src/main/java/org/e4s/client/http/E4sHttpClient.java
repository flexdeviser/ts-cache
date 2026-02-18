package org.e4s.client.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;
import org.e4s.client.E4sClient;
import org.e4s.model.MeterReading;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP-based implementation of {@link E4sClient} using REST API.
 * 
 * <p>This client communicates with the e4s-server via HTTP REST endpoints,
 * using JSON for serialization. It is suitable for:
 * <ul>
 *   <li>Cross-language clients (any language with HTTP support)</li>
 *   <li>Remote clients that cannot connect directly to Hazelcast cluster</li>
 *   <li>Situations where firewall/network restricts direct Hazelcast access</li>
 * </ul>
 * 
 * <h2>Configuration</h2>
 * Default timeouts:
 * <ul>
 *   <li>Connect: 10 seconds</li>
 *   <li>Read: 30 seconds</li>
 *   <li>Write: 30 seconds</li>
 * </ul>
 * 
 * <h2>Performance</h2>
 * Compared to native Hazelcast client:
 * <ul>
 *   <li>Lower throughput due to HTTP overhead and JSON serialization</li>
 *   <li>Higher latency due to server-side JSON parsing</li>
 *   <li>Larger network payload (JSON vs binary)</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * E4sClient client = new E4sHttpClient("http://localhost:8080");
 * 
 * // Use client...
 * client.ingestReading("MTR-001", reading);
 * 
 * // Close when done (or use try-with-resources)
 * client.close();
 * }</pre>
 * 
 * @see E4sClient
 */
public class E4sHttpClient implements E4sClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public E4sHttpClient(String baseUrl) {
        this(baseUrl, defaultHttpClient(), defaultObjectMapper());
    }

    public E4sHttpClient(String baseUrl, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    private static OkHttpClient defaultHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    @Override
    public void ingestReading(String meterId, MeterReading reading) {
        String url = baseUrl + "/api/v1/ingest?meterId=" + meterId;
        post(url, reading);
    }

    @Override
    public void ingestReadings(String meterId, List<MeterReading> readings) {
        String url = baseUrl + "/api/v1/ingest/batch?meterId=" + meterId;
        post(url, readings);
    }

    @Override
    public void ingestBatch(List<IngestRequest> requests) {
        String url = baseUrl + "/api/v1/batch";
        post(url, requests);
    }

    @Override
    public List<MeterReading> queryRange(String meterId, Instant start, Instant end) {
        String url = baseUrl + "/api/v1/meters/" + meterId + "/data?start=" + start + "&end=" + end;
        String response = get(url);
        try {
            QueryResponse queryResponse = objectMapper.readValue(response, QueryResponse.class);
            return queryResponse.getReadings();
        } catch (IOException e) {
            throw new E4sClientException("Failed to parse query response", e);
        }
    }

    @Override
    public AggregationResult queryAggregation(String meterId, Instant start, Instant end,
                                               AggregationType type, Interval interval) {
        String url = baseUrl + "/api/v1/meters/" + meterId + "/aggregate" +
                "?start=" + start + "&end=" + end + "&type=" + type + "&interval=" + interval;
        String response = get(url);
        try {
            return objectMapper.readValue(response, AggregationResult.class);
        } catch (IOException e) {
            throw new E4sClientException("Failed to parse aggregation response", e);
        }
    }

    @Override
    public CacheStats getCacheStats() {
        String url = baseUrl + "/api/v1/cache/stats";
        String response = get(url);
        try {
            return objectMapper.readValue(response, CacheStats.class);
        } catch (IOException e) {
            throw new E4sClientException("Failed to parse cache stats response", e);
        }
    }

    @Override
    public long getBucketCount() {
        CacheStats stats = getCacheStats();
        return stats.getTotalEntries();
    }

    @Override
    public boolean isHealthy() {
        try {
            String url = baseUrl + "/actuator/health";
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    private String get(String url) {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new E4sClientException("GET request failed: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new E4sClientException("Empty response body");
            }
            return body.string();
        } catch (IOException e) {
            throw new E4sClientException("GET request failed", e);
        }
    }

    private void post(String url, Object body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new E4sClientException("Failed to serialize request body", e);
        }

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new E4sClientException("POST request failed: " + response.code());
            }
        } catch (IOException e) {
            throw new E4sClientException("POST request failed", e);
        }
    }

    private static class QueryResponse {
        private List<MeterReading> readings;

        public List<MeterReading> getReadings() {
            return readings;
        }

        public void setReadings(List<MeterReading> readings) {
            this.readings = readings;
        }
    }
}
