package org.e4s.client.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;
import org.e4s.client.E4sClient;
import org.e4s.model.Timestamped;
import org.e4s.model.dynamic.DynamicModelRegistry;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class E4sHttpClient implements E4sClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public E4sHttpClient(String baseUrl) {
        this(baseUrl, null);
    }

    public E4sHttpClient(String baseUrl, String modelsPath) {
        this(baseUrl, defaultHttpClient(), defaultObjectMapper(), modelsPath);
    }

    public E4sHttpClient(String baseUrl, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this(baseUrl, httpClient, objectMapper, null);
    }

    public E4sHttpClient(String baseUrl, OkHttpClient httpClient, ObjectMapper objectMapper, String modelsPath) {
        DynamicModelRegistry.getInstance().initialize(modelsPath);
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

    public void validateModelsMatchServer() {
        try {
            String url = baseUrl + "/api/v1/models/hash";
            String response = get(url);
            
            int hashStart = response.indexOf("\"hash\":\"");
            if (hashStart >= 0) {
                hashStart += 8;
                int hashEnd = response.indexOf("\"", hashStart);
                String serverHash = response.substring(hashStart, hashEnd);
                
                DynamicModelRegistry.getInstance().validateHashMatch(serverHash);
            }
        } catch (Exception e) {
            throw new E4sClientException("Failed to validate models with server", e);
        }
    }

    @Override
    public void ingestReading(String meterId, Timestamped reading) {
        String url = baseUrl + "/api/v1/ingest?meterId=" + meterId;
        post(url, reading);
    }

    @Override
    public void ingestReadings(String meterId, List<? extends Timestamped> readings) {
        String url = baseUrl + "/api/v1/ingest/batch?meterId=" + meterId;
        post(url, readings);
    }

    @Override
    public void ingestBatch(List<IngestRequest> requests) {
        String url = baseUrl + "/api/v1/batch";
        post(url, requests);
    }

    @Override
    public List<? extends Timestamped> queryRange(String meterId, Instant start, Instant end) {
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
        private List<Timestamped> readings;

        public List<Timestamped> getReadings() {
            return readings;
        }

        public void setReadings(List<Timestamped> readings) {
            this.readings = readings;
        }
    }
}
