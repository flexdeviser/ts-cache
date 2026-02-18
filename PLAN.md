# Future Plan: Native Hazelcast Client with Client-Side Serialization

## Current Architecture (HTTP Client)

```
┌──────────────┐     HTTP/JSON      ┌──────────────┐
│  e4s-client  │ ─────────────────► │  e4s-server  │
│  (HTTP)      │                    │  (Hazelcast) │
│              │ ◄───────────────── │              │
│              │     JSON resp      │              │
└──────────────┘                    └──────────────┘
```

### Problems with Current Approach
- JSON serialization overhead on server
- Larger network payload
- Server CPU pressure from serialization

---

## Proposed Architecture (Native Client)

```
┌──────────────┐     Binary/HZ      ┌──────────────┐
│ e4s-hzclient │ ═══════════════►   │  e4s-server  │
│ (Native HZ)  │   Kryo+Deflater    │  (Hazelcast) │
│              │                    │  (cache only)│
│ Serialization│                    │  Eviction    │
│ on client    │ ◄═══════════════   │  Backup      │
└──────────────┘    Binary data     └──────────────┘
```

### Benefits
- **90% smaller network payload** (Kryo + Deflater)
- **Zero serialization CPU on server** - server focuses purely on caching/eviction
- **Better throughput** for both read and write operations
- **Lower latency** - binary protocol vs HTTP/JSON

---

## New Module: `e4s-hzclient`

### Features
- Hazelcast native client connection
- Client-side Kryo + Deflater serialization (reuses `e4s-model` serializers)
- Direct IMap access for put/get operations
- Same `E4sClient` API interface for consistency

### Dependencies
```xml
<dependencies>
    <dependency>
        <groupId>org.e4s</groupId>
        <artifactId>e4s-model</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.hazelcast</groupId>
        <artifactId>hazelcast</artifactId>
        <version>5.3.6</version>
    </dependency>
</dependencies>
```

### Project Structure
```
e4s-hzclient/
├── pom.xml
└── src/main/java/org/e4s/client/hazelcast/
    ├── E4sHzClient.java           # Implements E4sClient
    └── HazelcastClientConfig.java # Client configuration
```

---

## Implementation Steps

| Step | Task | Details |
|------|------|---------|
| 1 | Create `e4s-hzclient` module | pom.xml, add to parent pom modules |
| 2 | Implement `E4sHzClient` class | Implements `E4sClient` interface |
| 3 | Configure Hazelcast client | Connect to server cluster, register serializers |
| 4 | Update server config | Enable client-server mode, configure network discovery |
| 5 | Add connection management | Handle connect/reconnect, connection pooling |
| 6 | Update benchmarks | Compare HTTP vs Native client performance |
| 7 | Documentation | Update README with usage examples |

---

## Server Configuration Changes

### Enable Client-Server Mode

Current (embedded):
```java
Hazelcast.newHazelcastInstance(config);
```

New (server mode with client support):
```yaml
hazelcast:
  network:
    join:
      multicast:
        enabled: false
      tcp-ip:
        enabled: true
        member-list:
          - server1:5701
          - server2:5701
  serialization:
    custom-serializers:
      - type-class: org.e4s.model.MeterReading
        class-name: org.e4s.model.serialization.MeterReadingHazelcastSerializer
      - type-class: org.e4s.model.MeterBucket
        class-name: org.e4s.model.serialization.MeterBucketHazelcastSerializer
```

---

## Client Usage Example

```java
// HTTP Client (current)
E4sClient httpClient = new E4sHttpClient("http://localhost:8080");

// Native Hazelcast Client (new)
E4sClient hzClient = new E4sHzClient("localhost:5701");

// Same API for both
hzClient.ingestReading("MTR-001", reading);
List<MeterReading> data = hzClient.queryRange("MTR-001", start, end);
```

---

## Performance Expectations

| Metric | HTTP Client | Native Client (expected) |
|--------|-------------|--------------------------|
| Ingest throughput | ~50K/sec | ~100K+ /sec |
| Query throughput | ~69K/sec | ~150K+ /sec |
| Network payload | JSON (~2KB/bucket) | Binary (~200 bytes/bucket) |
| Server CPU | Serialization overhead | Minimal (cache ops only) |

---

## Questions to Consider

1. **Connection mode:** Should the client connect to a specific member or use smart routing?
2. **Failover:** How should the client handle server failures? Auto-reconnect?
3. **Authentication:** Do we need security/credentials for client connections?
4. **Coexistence:** Should both HTTP and Native clients be supported simultaneously?
5. **Near Cache:** Should client have a near cache for frequently accessed meters?

---

## Timeline

| Day | Tasks |
|-----|-------|
| Day 1 | Create e4s-hzclient module, basic implementation |
| Day 2 | Server configuration for client-server mode |
| Day 3 | Benchmarks and performance testing |
| Day 4 | Documentation and cleanup |

---

## Related Files

- `e4s-model/src/main/java/org/e4s/model/serialization/` - Serializers shared by client
- `e4s-client/src/main/java/org/e4s/client/E4sClient.java` - Interface to implement
- `e4s-server/src/main/java/org/e4s/server/config/HazelcastConfig.java` - Server config
