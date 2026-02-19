package org.e4s.model.dynamic;

import org.e4s.model.GenericBucket;
import org.e4s.model.Timestamped;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DynamicModelGeneratorTest {

    private static Map<String, Class<?>> generatedClasses;
    private static Map<String, ModelDefinition> definitions;

    @BeforeAll
    static void setUp() {
        ModelDefinitionLoader loader = new ModelDefinitionLoader();
        List<ModelDefinition> modelDefinitions = loader.load("models.xml");
        
        definitions = new HashMap<>();
        for (ModelDefinition def : modelDefinitions) {
            definitions.put(def.getName(), def);
        }
        
        ByteBuddyClassGenerator generator = new ByteBuddyClassGenerator();
        generatedClasses = generator.generateAllClasses(definitions);
    }

    @Test
    void shouldGenerateMeterReadingClass() {
        assertTrue(generatedClasses.containsKey("MeterReading"));
        Class<?> readingClass = generatedClasses.get("MeterReading");
        assertEquals("org.e4s.model.dynamic.MeterReading", readingClass.getName());
    }

    @Test
    void shouldNotGenerateMeterBucketClass() {
        assertFalse(generatedClasses.containsKey("MeterBucket"));
    }

    @Test
    void shouldHaveMeterReadingFields() throws Exception {
        Class<?> readingClass = generatedClasses.get("MeterReading");
        
        assertNotNull(readingClass.getDeclaredField("reportedTs"));
        assertNotNull(readingClass.getDeclaredField("voltage"));
        assertNotNull(readingClass.getDeclaredField("current"));
        assertNotNull(readingClass.getDeclaredField("power"));
    }

    @Test
    void shouldHaveMeterReadingGettersAndSetters() throws Exception {
        Class<?> readingClass = generatedClasses.get("MeterReading");
        
        assertNotNull(readingClass.getMethod("getReportedTs"));
        assertNotNull(readingClass.getMethod("setReportedTs", long.class));
        assertNotNull(readingClass.getMethod("getVoltage"));
        assertNotNull(readingClass.getMethod("setVoltage", double.class));
        assertNotNull(readingClass.getMethod("getCurrent"));
        assertNotNull(readingClass.getMethod("setCurrent", double.class));
        assertNotNull(readingClass.getMethod("getPower"));
        assertNotNull(readingClass.getMethod("setPower", double.class));
    }

    @Test
    void shouldImplementTimestamped() {
        Class<?> readingClass = generatedClasses.get("MeterReading");
        assertTrue(Timestamped.class.isAssignableFrom(readingClass));
    }

    @Test
    void shouldHaveGetTimestampMethod() throws Exception {
        Class<?> readingClass = generatedClasses.get("MeterReading");
        assertNotNull(readingClass.getMethod("getTimestamp"));
    }

    @Test
    void shouldCreateMeterReadingInstance() throws Exception {
        Class<?> readingClass = generatedClasses.get("MeterReading");
        Object instance = readingClass.getDeclaredConstructor().newInstance();
        
        Method setReportedTs = readingClass.getMethod("setReportedTs", long.class);
        setReportedTs.invoke(instance, 1234567890L);
        
        Method getReportedTs = readingClass.getMethod("getReportedTs");
        long reportedTs = (Long) getReportedTs.invoke(instance);
        
        assertEquals(1234567890L, reportedTs);
    }

    @Test
    void shouldGetTimestampReturnReportedTs() throws Exception {
        Class<?> readingClass = generatedClasses.get("MeterReading");
        Object instance = readingClass.getDeclaredConstructor().newInstance();
        
        Method setReportedTs = readingClass.getMethod("setReportedTs", long.class);
        setReportedTs.invoke(instance, 1234567890L);
        
        Method getTimestamp = readingClass.getMethod("getTimestamp");
        long timestamp = (Long) getTimestamp.invoke(instance);
        
        assertEquals(1234567890L, timestamp);
    }

    @Test
    void shouldCreateReadingUsingRegistry() {
        DynamicModelRegistry registry = DynamicModelRegistry.getInstance();
        registry.initialize();
        
        Map<String, Object> fieldValues = new HashMap<>();
        fieldValues.put("reportedTs", 1234567890L);
        fieldValues.put("voltage", 220.5);
        fieldValues.put("current", 5.2);
        fieldValues.put("power", 1146.6);
        
        Timestamped reading = registry.createReading("MeterReading", fieldValues);
        
        assertEquals(1234567890L, reading.getTimestamp());
        assertEquals(1234567890L, (Long) registry.getFieldValue(reading, "reportedTs"));
        assertEquals(220.5, (Double) registry.getFieldValue(reading, "voltage"), 0.001);
        assertEquals(5.2, (Double) registry.getFieldValue(reading, "current"), 0.001);
        assertEquals(1146.6, (Double) registry.getFieldValue(reading, "power"), 0.001);
    }

    @Test
    void shouldCreateBucketUsingRegistry() {
        DynamicModelRegistry registry = DynamicModelRegistry.getInstance();
        registry.initialize();
        
        GenericBucket<Timestamped> bucket = registry.createBucket("MeterReading", "MTR-001", 19500L);
        
        assertEquals("MTR-001", bucket.getId());
        assertEquals(19500L, bucket.getBucketDateEpochDay());
        assertEquals(0, bucket.getReadingCount());
    }

    @Test
    void shouldAddReadingToBucket() {
        DynamicModelRegistry registry = DynamicModelRegistry.getInstance();
        registry.initialize();
        
        Map<String, Object> fieldValues = new HashMap<>();
        fieldValues.put("reportedTs", 1234567890L);
        fieldValues.put("voltage", 220.5);
        fieldValues.put("current", 5.2);
        fieldValues.put("power", 1146.6);
        
        Timestamped reading = registry.createReading("MeterReading", fieldValues);
        GenericBucket<Timestamped> bucket = registry.createBucket("MeterReading", "MTR-001", 19500L);
        
        bucket.addReading(reading);
        
        assertEquals(1, bucket.getReadingCount());
    }

    @Test
    void shouldDeduplicateReadingsByTimestamp() {
        DynamicModelRegistry registry = DynamicModelRegistry.getInstance();
        registry.initialize();
        
        GenericBucket<Timestamped> bucket = registry.createBucket("MeterReading", "MTR-001", 19500L);
        
        Map<String, Object> fieldValues1 = new HashMap<>();
        fieldValues1.put("reportedTs", 1234567890L);
        fieldValues1.put("voltage", 220.5);
        fieldValues1.put("current", 5.2);
        fieldValues1.put("power", 1146.6);
        Timestamped reading1 = registry.createReading("MeterReading", fieldValues1);
        
        Map<String, Object> fieldValues2 = new HashMap<>();
        fieldValues2.put("reportedTs", 1234567890L);
        fieldValues2.put("voltage", 221.0);
        fieldValues2.put("current", 5.3);
        fieldValues2.put("power", 1171.3);
        Timestamped reading2 = registry.createReading("MeterReading", fieldValues2);
        
        bucket.addReading(reading1);
        bucket.addReading(reading2);
        
        assertEquals(1, bucket.getReadingCount());
        assertEquals(221.0, (Double) registry.getFieldValue(bucket.getReadings()[0], "voltage"), 0.001);
    }
}
