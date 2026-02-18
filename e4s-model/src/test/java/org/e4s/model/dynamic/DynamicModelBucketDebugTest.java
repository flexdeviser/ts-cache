package org.e4s.model.dynamic;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DynamicModelBucketDebugTest {

    private static Map<String, Class<?>> generatedClasses;
    private static Map<String, ModelDefinition> definitions;

    @BeforeAll
    static void setUp() {
        ModelDefinitionLoader loader = new ModelDefinitionLoader();
        List<ModelDefinition> modelDefinitions = loader.load("models.xml");
        
        definitions = new java.util.HashMap<>();
        for (ModelDefinition def : modelDefinitions) {
            definitions.put(def.getName(), def);
        }
        
        ByteBuddyClassGenerator generator = new ByteBuddyClassGenerator();
        generatedClasses = generator.generateAllClasses(definitions);
    }

    @Test
    void debugAddReading() throws Exception {
        Class<?> bucketClass = generatedClasses.get("MeterBucket");
        Class<?> readingClass = Class.forName("org.e4s.model.MeterReading");
        
        Object bucket = bucketClass.getDeclaredConstructor().newInstance();
        System.out.println("=== Initial bucket state ===");
        for (Field f : bucketClass.getDeclaredFields()) {
            f.setAccessible(true);
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                System.out.println(f.getName() + " = " + f.get(bucket));
            }
        }
        
        Object reading = readingClass.getConstructor().newInstance();
        Method setReportedTs = readingClass.getMethod("setReportedTs", long.class);
        setReportedTs.invoke(reading, 1234567890L);
        
        System.out.println("\n=== Calling addReading ===");
        Method addReading = bucketClass.getMethod("addReading", readingClass);
        addReading.invoke(bucket, reading);
        
        System.out.println("\n=== After addReading ===");
        for (Field f : bucketClass.getDeclaredFields()) {
            f.setAccessible(true);
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                System.out.println(f.getName() + " = " + f.get(bucket));
            }
        }
    }
}
