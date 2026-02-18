package org.e4s.model.dynamic;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DynamicModelGeneratorDebugTest {

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
    void debugBucketMethods() {
        Class<?> bucketClass = generatedClasses.get("MeterBucket");
        System.out.println("=== Bucket methods ===");
        for (Method m : bucketClass.getDeclaredMethods()) {
            System.out.println(m);
        }
        System.out.println("=== Bucket fields ===");
        for (java.lang.reflect.Field f : bucketClass.getDeclaredFields()) {
            System.out.println(f);
        }
    }
    
    @Test
    void debugReadingClass() {
        Class<?> readingClass = generatedClasses.get("MeterReading");
        System.out.println("=== Reading class: " + readingClass.getName());
    }
}
