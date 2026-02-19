package org.e4s.client;

import org.e4s.model.Timestamped;

import java.io.Closeable;
import java.time.Instant;
import java.util.List;

public interface E4sClient extends Closeable {

    void ingestReading(String meterId, Timestamped reading);

    void ingestReadings(String meterId, List<? extends Timestamped> readings);

    void ingestBatch(List<IngestRequest> requests);

    List<Timestamped> queryRange(String meterId, Instant start, Instant end);

    AggregationResult queryAggregation(String meterId, Instant start, Instant end,
                                        AggregationType type, Interval interval);

    CacheStats getCacheStats();

    long getBucketCount();

    boolean isHealthy();

    @Override
    void close();
}
