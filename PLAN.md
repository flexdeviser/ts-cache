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

---

# Plan: Dynamic Model Generation with ByteBuddy

**Status:** In Progress

## Overview

Generate `MeterReading` and `MeterBucket` classes dynamically from XML definitions at application startup using ByteBuddy, with dynamically generated Kryo serializers for optimal performance.

---

## XML Schema Design

**Enhanced `models.xml` schema:**

```xml
<datastore>
  <!-- Reading Model: The time-series data point -->
  <model name="MeterReading" package="org.e4s.model.dynamic">
    <field name="reportedTs" type="long"/>
    <field name="voltage" type="double"/>
    <field name="current" type="double"/>
    <field name="power" type="double"/>
  </model>
  
  <!-- Bucket Model: Container for daily readings -->
  <model name="MeterBucket" package="org.e4s.model.dynamic">
    <field name="meterId" type="String"/>
    <field name="bucketDateEpochDay" type="long"/>
    <field name="readings" type="array" elementType="MeterReading"/>
    <field name="readingCount" type="int"/>
    <field name="lastAccessTime" type="long"/>
    <field name="createdTime" type="long"/>
    
    <!-- Business methods to generate -->
    <method name="addReading" type="business" logic="dedupeByTimestamp"/>
    <method name="touch" type="business"/>
    <method name="trimToSize" type="business"/>
  </model>
</datastore>
```

**Supported field types:**
| XML Type | Java Type | Kryo Write Method |
|----------|-----------|-------------------|
| `string` | `String` | `writeString()` |
| `long` | `long` | `writeLong()` |
| `int` | `int` | `writeInt()` |
| `double` | `double` | `writeDouble()` |
| `float` | `float` | `writeFloat()` |
| `boolean` | `boolean` | `writeBoolean()` |
| `array` | `T[]` | Custom serialization |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Application Startup                        │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ModelDefinitionLoader                        │
│  - Parse models.xml from classpath                              │
│  - Create ModelDefinition objects                               │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ByteBuddyClassGenerator                      │
│  - Generate POJO classes (fields, getters, setters)             │
│  - Generate constructors (default + all-args)                   │
│  - Generate toString()                                          │
│  - Generate business methods (addReading, touch, etc.)          │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    KryoSerializerGenerator                      │
│  - Generate type-specific Kryo Serializer classes               │
│  - Direct field access for optimal performance                  │
│  - Same performance as current hardcoded serializers            │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DynamicModelRegistry                         │
│  - Store Class<?> references                                    │
│  - Store Serializer instances                                   │
│  - Provide lookup by model name                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
e4s-model/
├── src/main/
│   ├── java/org/e4s/model/
│   │   ├── dynamic/                    # NEW PACKAGE
│   │   │   ├── ModelDefinition.java    # POJO for XML model definition
│   │   │   ├── FieldDefinition.java    # POJO for field definition
│   │   │   ├── ModelDefinitionLoader.java  # Parse XML
│   │   │   ├── ByteBuddyClassGenerator.java # Generate classes
│   │   │   ├── KryoSerializerGenerator.java # Generate serializers
│   │   │   └── DynamicModelRegistry.java    # Runtime lookup
│   │   ├── MeterDayKey.java            # Keep as-is (utility class)
│   │   └── serialization/
│   │       └── DynamicKryoFactory.java # NEW: Uses generated serializers
│   └── resources/
│       └── models.xml                  # Model definitions
```

---

## Implementation Steps

| Step | Task | Status |
|------|------|--------|
| 1 | Add ByteBuddy dependency to pom.xml | ✅ Complete |
| 2 | Create XML schema & parser (ModelDefinition, FieldDefinition, ModelDefinitionLoader) | ✅ Complete |
| 3 | Implement ByteBuddyClassGenerator (fields, getters, setters) | ✅ Complete |
| 4 | Generate business methods (addReading, touch, trimToSize) | ✅ Complete |
| 5 | Implement KryoSerializerGenerator | ✅ Complete |
| 6 | Create DynamicModelRegistry | ✅ Complete |
| 7 | Create DynamicKryoFactory | ✅ Complete |
| 8 | Create Models helper class | ✅ Complete |
| 9 | Update Hazelcast serializers registration | Pending |
| 10 | Update dependent modules (e4s-server, e4s-hzclient) | Pending |
| 11 | Add tests | ✅ Complete (9/9 passing) |

### Architecture Options for Full Migration

**Option A: Interface-based (Recommended)**
1. Convert MeterReading, MeterBucket to interfaces
2. Generate dynamic implementations
3. All code references interfaces, uses factory methods

**Option B: Reflection-based**
1. Delete hardcoded classes
2. Use Models helper class everywhere
3. Slower due to reflection

**Option C: Hybrid**
1. Keep hardcoded classes as defaults
2. Allow dynamic models for custom types
3. Backward compatible

### Current Implementation

The dynamic model system generates classes at runtime:
- `org.e4s.model.dynamic.MeterReading`
- `org.e4s.model.dynamic.MeterBucket`

Use `Models` helper class to work with dynamic models:
```java
Models.initialize();
Object reading = Models.newReading(ts, 220.5, 5.2, 1146.6);
Object bucket = Models.newBucket("MTR-001", epochDay);
Models.addReading(bucket, reading);
``` |

---

## ByteBuddy Code Examples

**Generating a POJO class:**

```java
public class ByteBuddyClassGenerator {
    
    public Class<?> generateClass(ModelDefinition definition) {
        DynamicType.Builder<?> builder = new ByteBuddy()
            .subclass(Object.class)
            .name(definition.getFullName());
        
        // Add fields
        for (FieldDefinition field : definition.getFields()) {
            builder = builder.defineField(field.getName(), field.getType(), Modifier.PRIVATE);
        }
        
        // Add getters and setters
        for (FieldDefinition field : definition.getFields()) {
            builder = builder.defineMethod("get" + capitalize(field.getName()), 
                    field.getType(), Modifier.PUBLIC)
                .intercept(FieldAccessor.ofField(field.getName()));
            
            builder = builder.defineMethod("set" + capitalize(field.getName()),
                    void.class, Modifier.PUBLIC)
                .withParameter(field.getType(), "value")
                .intercept(FieldAccessor.ofField(field.getName()));
        }
        
        // Add all-args constructor
        builder = addConstructor(builder, definition);
        
        return builder.make()
            .load(getClass().getClassLoader())
            .getLoaded();
    }
}
```

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| ByteBuddy class generation overhead | Low | Only runs once at startup (~50-100ms) |
| Generated serializer correctness | High | Comprehensive unit tests, compare with hardcoded output |
| Complex business methods (addReading) | Medium | Use bytecode generation or MethodDelegation |
| Debugging dynamic classes | Medium | Generate toString(), add source file naming |
| Performance regression | High | Benchmark against current hardcoded implementation |

---

## Timeline Estimate

| Day | Tasks |
|-----|-------|
| Day 1 | XML schema, parser, ModelDefinition classes |
| Day 2 | ByteBuddyClassGenerator for simple POJOs |
| Day 3 | Business method generation (addReading, etc.) |
| Day 4 | KryoSerializerGenerator |
| Day 5 | DynamicModelRegistry, integration with KryoFactory |
| Day 6 | Update e4s-server, e4s-hzclient |
| Day 7 | Testing, benchmarking, documentation |