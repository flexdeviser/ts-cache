# E4S Server - Requirements Document

## Project Overview
**Module Name:** e4s-server  
**Parent Project:** ts-cache  
**Technology Stack:** Spring Boot 3.2.0, Java 17, Hazelcast 5.3.6, Kryo 5.5.0

---

## Technical Requirements

### Infrastructure
| Requirement | Status | Details |
|-------------|--------|---------|
| JDK Version | ✅ Configured | 17.0.18-amzn (via `.sdkmanrc`) |
| Spring Boot | ✅ Configured | Version 3.2.0 |
| Build Tool | ✅ Configured | Maven (multi-module) |

### Dependencies
| Dependency | Status | Purpose |
|------------|--------|---------|
| spring-boot-starter-web | ✅ Added | REST API support |
| spring-boot-starter-actuator | ✅ Added | Health monitoring & metrics |
| spring-boot-starter-test | ✅ Added | Testing framework |
| hazelcast | ✅ Added | In-memory data grid (v5.3.6) |
| hazelcast-spring | ✅ Added | Spring integration |
| kryo | ✅ Added | High-performance serialization (v5.5.0) |

### Configuration
| Configuration | Status | Value |
|---------------|--------|-------|
| Server Port | ✅ Configured | 8080 |
| Application Name | ✅ Configured | e4s-server |
| Actuator Endpoints | ✅ Configured | health, info |

---

## Functional Requirements

### Application Overview
E4S Server is a **time-series hot cache system** designed to hold meter data for energy monitoring.

**Key Characteristics:**
- **In-Memory Only:** No persistence to database
- **Purpose:** Reduce slow database operations by caching frequently accessed data
- **Data Source:** Loaded from database on-demand (cold start) or via real-time ingestion

---

### Data Model
| Field | Type | Description |
|-------|------|-------------|
| meterId | String/Long | Unique identifier for the meter |
| reportedTs | Timestamp | Time when the reading was reported |
| voltage | Decimal | Voltage reading |
| current | Decimal | Current reading |
| power | Decimal | Power reading |
| *additional fields* | - | To be defined |

### Data Partitioning Strategy
- **Partition Key:** Day (based on reportedTs)
- **Retention Period:** 3 weeks (21 days)
- **Data Buckets per Meter:** 21 daily buckets

### Scale Requirements
| Metric | Value |
|--------|-------|
| Number of Meters | ~1,000,000 |
| Days of Data per Meter | 21 days |
| Total Data Buckets | ~21,000,000 |

### Key Design Considerations
- **Memory Management:** Efficient storage for high-volume time-series data
- **Data Eviction:** Automatic cleanup of data older than 21 days
- **Query Performance:** Fast retrieval by meterId and time range
- **Write Throughput:** Handle high-frequency meter readings

---

## Data Ingestion Pattern

### Ingestion Modes

#### 1. Batch Ingestion (Cold Start)
- **Trigger:** Application startup or cache miss
- **Purpose:** Load historical data from database
- **Volume:** 21 days × 1,000,000 meters = up to 21 million records
- **Use Case:** Populate empty cache with historical data on-demand

#### 2. Real-time Ingestion
- **Frequency:** Every 15 minutes per meter
- **Source:** Meter collection systems
- **Pattern:** Staggered arrival (not all meters report simultaneously)
- **Estimated Throughput:** ~1,100-1,200 writes/second (distributed)

### Ingestion Flow
```
┌─────────────────┐     ┌─────────────────┐
│    Database     │     │ Meter Collectors│
│  (Historical)   │     │  (Real-time)    │
└────────┬────────┘     └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────────────────────────┐
│        E4S Server (Cache)           │
│                                     │
│  - Batch API: Load historical data  │
│  - Real-time API: Ingest live data  │
└─────────────────────────────────────┘
```

### API Requirements
| API | Method | Purpose | Status |
|-----|--------|---------|--------|
| Batch Ingest | POST /batch | Load historical data | ✅ Complete |
| Real-time Ingest | POST /ingest | Single/batch real-time data | ✅ Complete |
| Query Range | GET /meters/{id}/data | Time range query (JSON) | ✅ Complete |
| Query Aggregation | GET /meters/{id}/aggregate | Aggregated data | ✅ Complete |
| Cache Stats | GET /cache/stats | Cache statistics | ✅ Complete |
| Memory Usage | GET /cache/memory | Memory usage summary | ✅ Complete |
| Java Client | - | Native Java serialization | 🔲 Pending |

### Ingestion Considerations
- **Idempotency:** Handle duplicate submissions gracefully
- **Out-of-order:** Accept data with past timestamps (within retention window)
- **Backpressure:** Handle burst loads during batch operations

---

## Query Pattern

### Use Case
E4S Server acts as a **hot cache** for other applications to query meter data.

### Query Types

#### 1. Time Range Query
- **Purpose:** Retrieve raw data for a single meter within a time range
- **Parameters:** meterId, startDateTime, endDateTime
- **Returns:** List of meter readings

#### 2. Aggregation Query
- **Purpose:** Server-side aggregation of time-series data
- **Parameters:** meterId, startDateTime, endDateTime, aggregationType, interval
- **Aggregation Types:** SUM, AVG, MIN, MAX, COUNT
- **Interval Options:** Hourly, Daily, Weekly

### Response Formats

#### 1. JSON (REST API)
- **Pros:** Language-agnostic, easy to use
- **Cons:** Serialization overhead, slower performance
- **Use Case:** Cross-language clients, debugging

#### 2. Java Client (Binary Serialization)
- **Pros:** Faster serialization, lower overhead
- **Cons:** Java-only, requires client library
- **Use Case:** High-performance Java applications

### Query Flow
```
┌─────────────────┐     ┌─────────────────┐
│   Java Apps     │     │   Other Apps    │
│ (Java Client)   │     │  (REST/JSON)    │
└────────┬────────┘     └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────────────────────────┐
│        E4S Server (Cache)           │
│                                     │
│  - REST API: JSON response          │
│  - Java Client: Binary serialization│
│  - Aggregation: Server-side compute │
└─────────────────────────────────────┘
```

### Query Considerations
- **Performance:** Minimize serialization overhead for large datasets
- **Pagination:** Support for large result sets
- **Caching:** Consider caching frequent aggregation results
- **Client Library:** Provide Maven artifact for Java clients

---

## Data Eviction Strategy

### Eviction Policy
- **Retention Window:** 21 days (configurable)
- **Eviction Trigger:** Time-based + Access-based (LRU-like)

### Eviction Rules
A data bucket (daily partition) is eligible for eviction when **BOTH** conditions are met:

1. **Age Condition:** Bucket timestamp is older than 21 days
2. **Access Condition:** Bucket has not been accessed (read/write) within the last 24 hours

### Eviction Mechanism
```
┌─────────────────────────────────────────────────────────┐
│            Data Bucket Eligibility Check                │
├─────────────────────────────────────────────────────────┤
│                                                         │
│   ┌─────────────┐          ┌─────────────┐             │
│   │ Age > 21d?  │   AND    │ Not touched │             │
│   │             │          │  in 24h?    │             │
│   └──────┬──────┘          └──────┬──────┘             │
│          │                        │                     │
│          ▼                        ▼                     │
│   ┌─────────────────────────────────┐                  │
│   │    Eligible for Eviction        │                  │
│   └─────────────────────────────────┘                  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Eviction Process
- **Background Job:** Runs periodically (e.g., every hour)
- **Scan Strategy:** Iterate through all meter buckets
- **Action:** Remove eligible buckets from memory
- **Metrics:** Track evicted buckets, freed memory

### Access Tracking
- **Last Accessed Timestamp:** Updated on every read/write operation
- **Granularity:** Per bucket (daily partition)
- **Storage:** Lightweight metadata alongside bucket data

### Memory Management
- **Soft Limit:** Configurable max memory usage (warning threshold)
- **Hard Limit:** Reject writes when memory critical
- **Monitoring:** Track memory usage via actuator metrics
- **Emergency Eviction:** If memory critical, evict oldest buckets regardless of access time

---

## Hazelcast Implementation Approach

### Technology Decision
| Aspect | Decision | Rationale |
|--------|----------|-----------|
| Cache Engine | Hazelcast 5.x | Mature, distributed, high-performance IMDG |
| Deployment Mode | Embedded | Single-node simplicity, can scale to cluster later |
| Memory Format | BINARY | Lower memory footprint, faster serialization |
| Serialization | Kryo 5.5.0 + Deflater | 90% memory reduction vs Java serialization |
| Backup Count | 1 | Cluster resilience with 50% memory overhead |

### Capability Mapping
| Requirement | Hazelcast Feature | Status |
|-------------|-------------------|--------|
| In-Memory Storage | IMap with BINARY format | ✅ Native |
| Write Throughput (~1,200/sec) | Partitioned IMap | ✅ Handles 10K+ TPS |
| Horizontal Scaling | Partition distribution | ✅ Native |
| TTL-based Expiration | time-to-live-seconds | ⚠️ Needs customization |
| Custom Eviction (Age > 21d AND idle > 24h) | Custom EvictionComparator | 🔧 Requires implementation |
| Time-Range Queries | Predicates + Indexes | 🔧 Requires key design |
| Aggregation Queries | EntryProcessor | 🔧 Requires implementation |

### Data Model Design

#### Key Structure
```
Key = meterId:dayBucket
- meterId: String (e.g., "MTR-001")
- dayBucket: String (e.g., "2026-02-18")

Example: "MTR-001:2026-02-18" → List<MeterReading>
```

#### Value Structure
```java
public class MeterBucket implements Serializable {
    private String meterId;
    private LocalDate bucketDate;
    private List<MeterReading> readings;  // Sorted by reportedTs
    private long lastAccessTime;          // For custom eviction
    private long createdTime;
}

public class MeterReading implements Serializable {
    private Instant reportedTs;
    private BigDecimal voltage;
    private BigDecimal current;
    private BigDecimal power;
    // additional fields...
}
```

#### Key Design Rationale
- **Daily buckets**: Natural partition by day, aligns with eviction granularity
- **Composite key**: Enables efficient key scanning with `Predicate.sql("key LIKE 'MTR-001:%'")`
- **Sorted readings**: Optimizes time-range queries within a bucket

### Eviction Strategy

#### Challenge
Hazelcast provides:
- `time-to-live-seconds`: Removes X seconds after **last write** (resets on update)
- `max-idle-seconds`: Removes if not accessed for X seconds

These work as **OR**, but we need **AND**: `age > 21 days AND not accessed in 24 hours`.

#### Solution: Custom Eviction Comparator
```java
public class MeterBucketEvictor implements EvictionPolicyComparator<MeterBucket> {
    private static final long RETENTION_MS = 21L * 24 * 60 * 60 * 1000;
    private static final long IDLE_THRESHOLD_MS = 24L * 60 * 60 * 1000;

    @Override
    public int compare(MeterBucket o1, MeterBucket o2) {
        boolean e1 = isEvictable(o1);
        boolean e2 = isEvictable(o2);
        return Boolean.compare(e1, e2);
    }

    private boolean isEvictable(MeterBucket bucket) {
        long now = System.currentTimeMillis();
        long age = now - bucket.getCreatedTime();
        long idle = now - bucket.getLastAccessTime();
        return age > RETENTION_MS && idle > IDLE_THRESHOLD_MS;
    }
}
```

#### Alternative: Scheduled Eviction Job
If custom comparator proves complex, implement a scheduled job:
```java
@Scheduled(fixedRate = 3600000)  // Every hour
public void evictOldBuckets() {
    Predicate<String, MeterBucket> predicate = Predicates.and(
        Predicates.sql("createdTime < now - 21 days"),
        Predicates.sql("lastAccessTime < now - 24 hours")
    );
    imap.removeAll(predicate);
}
```

#### Access Tracking
Update `lastAccessTime` on every read/write:
```java
// On read
MeterBucket bucket = imap.get(key);
bucket.setLastAccessTime(System.currentTimeMillis());
imap.put(key, bucket);

// Or use EntryProcessor for atomic update
imap.executeOnKey(key, entry -> {
    entry.getValue().setLastAccessTime(System.currentTimeMillis());
    entry.getValue().getReadings().addAll(newReadings);
    return null;
});
```

### Query Implementation

#### 1. Time Range Query
```java
public List<MeterReading> queryRange(String meterId, Instant start, Instant end) {
    List<MeterReading> result = new ArrayList<>();
    
    // Calculate which day buckets we need
    List<String> bucketKeys = generateBucketKeys(meterId, start, end);
    
    // Fetch all buckets (can be parallelized)
    for (String key : bucketKeys) {
        MeterBucket bucket = imap.get(key);
        if (bucket != null) {
            bucket.getReadings().stream()
                .filter(r -> r.getReportedTs().isAfter(start) && r.getReportedTs().isBefore(end))
                .forEach(result::add);
        }
    }
    return result;
}
```

#### 2. Aggregation Query
```java
public AggregationResult queryAggregation(
    String meterId, Instant start, Instant end, 
    AggregationType type, Interval interval
) {
    List<MeterReading> readings = queryRange(meterId, start, end);
    
    // Group by interval
    Map<Instant, List<MeterReading>> grouped = readings.stream()
        .collect(Collectors.groupingBy(r -> truncateToInterval(r.getReportedTs(), interval)));
    
    // Aggregate
    return grouped.entrySet().stream()
        .map(e -> aggregate(e.getKey(), e.getValue(), type))
        .collect(Collectors.toList());
}
```

#### 3. Distributed Aggregation (for large datasets)
```java
public AggregationResult queryAggregationDistributed(
    String meterId, Instant start, Instant end
) {
    List<String> bucketKeys = generateBucketKeys(meterId, start, end);
    
    // Use EntryProcessor for server-side aggregation
    Map<String, AggregationResult> results = imap.executeOnKeys(
        new HashSet<>(bucketKeys),
        new AggregationEntryProcessor(start, end)
    );
    
    // Merge partial results
    return mergeResults(results.values());
}
```

### Hazelcast Configuration

```yaml
hazelcast:
  instance-name: e4s-server
  
  map:
    meter-data:
      in-memory-format: BINARY
      backup-count: 0
      time-to-live-seconds: 0          # Handled by custom eviction
      max-idle-seconds: 0              # Handled by custom eviction
      eviction:
        eviction-policy: LRU
        max-size-policy: USED_HEAP_PERCENTAGE
        size: 75
      indexes:
        - type: HASH
          attributes:
            - "meterId"
            - "bucketDate"
```

### Performance Considerations

| Aspect | Configuration | Rationale |
|--------|---------------|-----------|
| Partition Count | 271 (default) | Good for single-node, can increase for cluster |
| Serialization | Kryo + Deflater (level 6) | 90% smaller, 7x faster than Java serialization |
| Near Cache | Disabled | Single-node, no benefit |
| Async Operations | Use IMap async methods | Higher throughput for batch ingestion |
| Backup | 1 | Resilience for cluster deployment |

### Memory Estimation

**V1 (Java Serialization):**
```
Per bucket:
- Key (String): ~30 bytes (meterId:YYYY-MM-DD)
- Value (MeterBucket):
  - Header: ~50 bytes
  - Readings (avg 96/day, 15-min intervals): ~96 * 200 bytes = ~20 KB
  - Total per bucket: ~20 KB

Total memory:
- 21M buckets × 20 KB = ~420 GB (without compression)
- With 1 backup: ~840 GB
```

**V2 (Kryo + Deflater):**
```
Per bucket:
- Key (String): ~30 bytes
- Value (MeterBucketV2):
  - Header: ~50 bytes
  - Readings (avg 96/day): ~96 * 32 bytes = ~3 KB
  - After Deflater compression: ~1.5-2 KB
  - Total per bucket: ~1.5-2 KB

Total memory:
- 21M buckets × 2 KB = ~42 GB (compressed)
- With 1 backup: ~84 GB
- Savings: ~90%
```

### Implementation Phases

| Phase | Tasks | Status |
|-------|-------|--------|
| 1. Core Setup | Hazelcast config, data model, basic IMap | ✅ Complete |
| 2. Ingestion APIs | POST /batch, POST /ingest | ✅ Complete |
| 3. Query APIs | GET /meters/{id}/data, aggregation | ✅ Complete |
| 4. Eviction | Custom eviction job/comparator | ✅ Complete |
| 5. Memory Optimization | Kryo serialization, primitive types, compression | ✅ Complete |
| 6. Monitoring | Cache stats endpoint, memory usage tracking | ✅ Complete |
| 7. Java Client | Binary serialization client library | 🔲 Pending |

---

## Benchmark Results

### Test Environment
- **Hardware:** MacBook Pro (local development)
- **JDK:** Java 21.0.10
- **Hazelcast:** 5.3.6 (embedded, single node)
- **Spring Boot:** 3.2.0
- **Serialization:** Kryo 5.5.0 + Deflater (level 6)

### Benchmark 1: Single Ingest (800 meters, 76.8K readings)

| Metric | V1 (Java Ser) | V2 (Kryo) | Improvement |
|--------|---------------|-----------|-------------|
| Throughput | 6,967/sec | **50,843/sec** | **7.3x faster** |
| Avg Latency | 1.14 ms | **156 µs** | **7.3x faster** |
| Memory/Reading | ~200 bytes | **32 bytes** | **84% smaller** |

### Benchmark 2: Batch Ingest (4,000 meters, 384K readings)

| Metric | V1 (Java Ser) | V2 (Kryo) | Improvement |
|--------|---------------|-----------|-------------|
| Throughput | 17,929/sec | **45,109/sec** | **2.5x faster** |
| Avg Latency | 42.6 ms | **14.4 ms** | **3x faster** |
| Memory/Bucket | ~20 KB | **1.8 KB** | **91% smaller** |

### Benchmark 3: Range Query

| Metric | V1 (Java Ser) | V2 (Kryo) | Improvement |
|--------|---------------|-----------|-------------|
| Throughput | 45,231/sec | **69,296/sec** | **1.5x faster** |
| Avg Latency | 152 µs | **110 µs** | **38% faster** |

### Benchmark 4: Aggregation Query

| Metric | V1 (Java Ser) | V2 (Kryo) | Improvement |
|--------|---------------|-----------|-------------|
| Throughput | 29,701/sec | **35,357/sec** | **19% faster** |
| Avg Latency | 238 µs | **219 µs** | **8% faster** |

### Benchmark 5: Stress Test (16 threads, 1.5M readings)

| Metric | Value |
|--------|-------|
| Total Readings | 1,536,000 |
| Buckets Created | 32,000 |
| Duration | 30 seconds |
| Throughput | **51,212 readings/sec** |
| Total Memory | 68.3 MB |
| Avg Bucket Size | 2,238 bytes |

### Performance Summary (V2 Optimized)

| Operation | Throughput | Avg Latency | Memory/Reading |
|-----------|------------|-------------|----------------|
| Single Ingest | 50,843/sec | 156 µs | 32 bytes |
| Batch Ingest | 45,109/sec | 14.4 ms | 32 bytes |
| Range Query | 69,296/sec | 110 µs | - |
| Aggregation | 35,357/sec | 219 µs | - |

### Performance Comparison (V1 vs V2)

| Operation | V1 Throughput | V2 Throughput | Speedup |
|-----------|---------------|---------------|---------|
| Single Ingest | 6,967/sec | 50,843/sec | **7.3x** |
| Batch Ingest | 17,929/sec | 45,109/sec | **2.5x** |
| Range Query | 45,231/sec | 69,296/sec | **1.5x** |
| Aggregation | 29,701/sec | 35,357/sec | **1.2x** |

---

## Memory Optimization

### Implementation

| Aspect | Technology | Rationale |
|--------|------------|-----------|
| Serialization | **Kryo 5.5.0 + Deflater (level 6)** | 80%+ memory reduction |
| Data Types | `double`, `long` primitives | Eliminates BigDecimal/Instant overhead |
| Collection | Primitive array with trimToSize() | No ArrayList capacity overhead |
| Backup | 1 backup (50% overhead) | Cluster resilience |

### Optimized Data Model

```java
// MeterReadingV2 - 32 bytes (8+8+8+8)
public class MeterReadingV2 {
    long reportedTs;    // epoch millis
    double voltage;
    double current;
    double power;
}

// MeterBucketV2 - ~1.5-2 KB for 96 readings
public class MeterBucketV2 {
    String meterId;
    long bucketDateEpochDay;
    MeterReadingV2[] readings;  // trimmed array
    long lastAccessTime;
    long createdTime;
}
```

### Memory Benchmark Results

| Buckets | Readings | Memory (MB) | Bytes/Bucket | Bytes/Reading |
|---------|----------|-------------|--------------|---------------|
| 1,600 | 76,800 | 2.3 | 1,527 | 31.6 |
| 8,000 | 384,000 | 14.3 | 1,787 | 37.2 |
| 32,000 | 1,536,000 | 68.3 | 2,238 | 46.5 |

### Memory Projection for Full Scale

| Scale | Buckets | Readings | Memory (no backup) | Memory (1 backup) |
|-------|---------|----------|-------------------|-------------------|
| V2 Optimized | 21M | 2B | **~42 GB** | **~84 GB** |
| V1 (Java Ser) | 21M | 2B | ~400-500 GB | ~800-1000 GB |
| **Savings** | - | - | **~90%** | **~90%** |

### Cache Monitoring APIs

```bash
# Get detailed cache statistics
curl http://localhost:8080/api/v1/cache/stats

# Get memory usage summary
curl http://localhost:8080/api/v1/cache/memory
```

### Response Example

```json
{
    "bucketCount": 32000,
    "memoryBytes": 71624805,
    "memoryMB": 68.31,
    "memoryGB": 0.067,
    "avgBytesPerBucket": 2238
}
```

### Running Benchmarks

```bash
# Start the server
cd e4s-server && mvn spring-boot:run

# Run full benchmark (batch ingest + query + aggregation)
curl -X POST http://localhost:8080/api/v1/benchmark/full \
  -H "Content-Type: application/json" \
  -d '{"threadCount": 8, "metersPerThread": 250, "batchSize": 96}'

# Run specific benchmark
curl -X POST http://localhost:8080/api/v1/benchmark/ingest
curl -X POST http://localhost:8080/api/v1/benchmark/query
curl -X POST http://localhost:8080/api/v1/benchmark/aggregation

# Get default config
curl http://localhost:8080/api/v1/benchmark/config
```

### Benchmark Configuration Options

| Parameter | Default | Description |
|-----------|---------|-------------|
| threadCount | 4 | Number of concurrent threads |
| metersPerThread | 100 | Meters assigned per thread |
| readingsPerMeter | 96 | Readings per meter (96 = 1 day @ 15-min intervals) |
| batchSize | 96 | Readings per batch operation |
| queriesPerThread | 100 | Queries per thread for query benchmarks |

### Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Memory overflow | High | Kryo compression, heap limits, emergency eviction |
| Custom eviction complexity | Medium | Scheduled job with configurable retention |
| Hot partition (one meter heavily accessed) | Low | Data partitioned by bucket, not meter |
| Serialization overhead | Low | Kryo with Deflater compression (90% reduction) |
| Cluster node failure | Medium | 1 backup configured for resilience |

---

## Notes
- This document tracks all requirements and discussions for the e4s-server module
- Update this document as new requirements are identified

---

## Change Log
| Date | Change | Author |
|------|--------|--------|
| 2026-02-18 | Initial project setup | - |
| 2026-02-18 | Added SDKMAN configuration | - |
| 2026-02-18 | Documented time-series cache requirements | - |
| 2026-02-18 | Documented data ingestion pattern | - |
| 2026-02-18 | Documented query patterns | - |
| 2026-02-18 | Clarified in-memory only (no persistence) | - |
| 2026-02-18 | Documented detailed eviction strategy | - |
| 2026-02-18 | Documented Hazelcast implementation approach | - |
| 2026-02-18 | Completed POC implementation with benchmarks | - |
| 2026-02-18 | Implemented Kryo serialization with compression | - |
| 2026-02-18 | Achieved 90% memory reduction with optimized models | - |
| 2026-02-18 | Completed performance benchmarks (V1 vs V2 comparison) | - |
