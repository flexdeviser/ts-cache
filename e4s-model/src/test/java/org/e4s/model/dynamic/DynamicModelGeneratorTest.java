package org.e4s.model.dynamic;

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
    void shouldGenerateMeterBucketClass() {
        assertTrue(generatedClasses.containsKey("MeterBucket"));
        Class<?> bucketClass = generatedClasses.get("MeterBucket");
        assertEquals("org.e4s.model.dynamic.MeterBucket", bucketClass.getName());
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
    void shouldHaveMeterBucketFields() throws Exception {
        Class<?> bucketClass = generatedClasses.get("MeterBucket");
        
        assertNotNull(bucketClass.getDeclaredField("meterId"));
        assertNotNull(bucketClass.getDeclaredField("bucketDateEpochDay"));
        assertNotNull(bucketClass.getDeclaredField("readings"));
        assertNotNull(bucketClass.getDeclaredField("readingCount"));
        assertNotNull(bucketClass.getDeclaredField("lastAccessTime"));
        assertNotNull(bucketClass.getDeclaredField("createdTime"));
    }

    @Test
    void shouldHaveBucketBusinessMethods() throws Exception {
        Class<?> bucketClass = generatedClasses.get("MeterBucket");
        Class<?> readingClass = Class.forName("org.e4s.model.MeterReading");
        
        assertNotNull(bucketClass.getMethod("addReading", readingClass));
        assertNotNull(bucketClass.getMethod("touch"));
        assertNotNull(bucketClass.getMethod("trimToSize"));
    }

    @Test
    void shouldCreateMeterBucketInstance() throws Exception {
        Class<?> bucketClass = generatedClasses.get("MeterBucket");
        Object instance = bucketClass.getDeclaredConstructor().newInstance();
        
        Method setMeterId = bucketClass.getMethod("setMeterId", String.class);
        setMeterId.invoke(instance, "MTR-001");
        
        Method getMeterId = bucketClass.getMethod("getMeterId");
        String meterId = (String) getMeterId.invoke(instance);
        
        assertEquals("MTR-001", meterId);
    }

    @Test
    void shouldAddReadingToBucket() throws Exception {
        Class<?> bucketClass = generatedClasses.get("MeterBucket");
        Class<?> readingClass = Class.forName("org.e4s.model.MeterReading");
        
        Object bucket = bucketClass.getDeclaredConstructor().newInstance();
        Object reading = readingClass.getConstructor().newInstance();
        
        Method setReportedTs = readingClass.getMethod("setReportedTs", long.class);
        setReportedTs.invoke(reading, 1234567890L);
        
        Method setVoltage = readingClass.getMethod("setVoltage", double.class);
        setVoltage.invoke(reading, 220.5);
        
        Method addReading = bucketClass.getMethod("addReading", readingClass);
        addReading.invoke(bucket, reading);
        
        Method getReadingCount = bucketClass.getMethod("getReadingCount");
        int count = (Integer) getReadingCount.invoke(bucket);
        
        assertEquals(1, count);
    }
}
