package org.e4s.model.dynamic;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DynamicModelInterceptorDebugTest {

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
    void checkInterceptorDelegates() throws Exception {
        Class<?> bucketClass = generatedClasses.get("MeterBucket");
        
        System.out.println("=== Static delegate fields ===");
        for (Field f : bucketClass.getDeclaredFields()) {
            f.setAccessible(true);
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                System.out.println(f.getName() + " = " + f.get(null));
            }
        }
    }
}
