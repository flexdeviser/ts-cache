package org.e4s.model.dynamic;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ByteBuddyClassGenerator {

    private final Map<String, Class<?>> generatedClasses = new HashMap<>();
    private Map<String, ModelDefinition> allDefinitions;

    public Map<String, Class<?>> generateAllClasses(Map<String, ModelDefinition> definitions) {
        this.allDefinitions = definitions;
        Map<String, Class<?>> result = new LinkedHashMap<>();
        
        Map<String, ModelDefinition> sortedDefinitions = new LinkedHashMap<>();
        for (ModelDefinition def : definitions.values()) {
            if (!def.hasArrayField()) {
                sortedDefinitions.put(def.getName(), def);
            }
        }
        for (ModelDefinition def : definitions.values()) {
            if (def.hasArrayField()) {
                sortedDefinitions.put(def.getName(), def);
            }
        }
        
        List<DynamicType.Unloaded<?>> unloadedTypes = new ArrayList<>();
        Map<String, Integer> typeIndexMap = new HashMap<>();
        
        int index = 0;
        for (ModelDefinition def : sortedDefinitions.values()) {
            DynamicType.Unloaded<?> unloaded = generateUnloadedClass(def, definitions);
            unloadedTypes.add(unloaded);
            typeIndexMap.put(def.getName(), index);
            index++;
        }
        
        Map<String, Class<?>> loaded = loadAllClasses(unloadedTypes, typeIndexMap, sortedDefinitions);
        
        for (Map.Entry<String, Class<?>> entry : loaded.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
            generatedClasses.put(entry.getKey(), entry.getValue());
        }
        
        return result;
    }

    private Map<String, Class<?>> loadAllClasses(List<DynamicType.Unloaded<?>> unloadedTypes,
                                                   Map<String, Integer> typeIndexMap,
                                                   Map<String, ModelDefinition> definitions) {
        Map<String, Class<?>> result = new LinkedHashMap<>();
        
        ClassLoader classLoader = getClass().getClassLoader();
        
        for (int i = 0; i < unloadedTypes.size(); i++) {
            DynamicType.Unloaded<?> unloaded = unloadedTypes.get(i);
            Class<?> loadedClass = unloaded.load(classLoader).getLoaded();
            
            for (Map.Entry<String, Integer> entry : typeIndexMap.entrySet()) {
                if (entry.getValue() == i) {
                    result.put(entry.getKey(), loadedClass);
                    break;
                }
            }
        }
        
        return result;
    }

    private DynamicType.Unloaded<?> generateUnloadedClass(ModelDefinition definition,
                                                           Map<String, ModelDefinition> allDefinitions) {
        DynamicType.Builder<?> builder = new ByteBuddy()
                .subclass(Object.class)
                .name(definition.getFullName())
                .modifiers(Modifier.PUBLIC);

        for (FieldDefinition field : definition.getFields()) {
            Class<?> fieldType = resolveFieldType(field, allDefinitions);
            builder = builder.defineField(field.getName(), fieldType, Modifier.PRIVATE);
        }

        builder = addAllArgsConstructor(builder, definition, allDefinitions);
        
        for (FieldDefinition field : definition.getFields()) {
            builder = addGetter(builder, field, allDefinitions);
            builder = addSetter(builder, field, allDefinitions);
        }

        builder = addToString(builder, definition);

        if (isBucketModel(definition)) {
            builder = addBucketBusinessMethods(builder, definition, allDefinitions);
        }

        return builder.make();
    }

    public Class<?> generateClass(ModelDefinition definition, Map<String, ModelDefinition> allDefinitions) {
        DynamicType.Unloaded<?> unloaded = generateUnloadedClass(definition, allDefinitions);
        return unloaded.load(getClass().getClassLoader()).getLoaded();
    }

    private Class<?> resolveFieldType(FieldDefinition field, Map<String, ModelDefinition> allDefinitions) {
        if (field.getType() == FieldType.ARRAY) {
            Class<?> elementType = resolveElementType(field.getElementType(), allDefinitions);
            return java.lang.reflect.Array.newInstance(elementType, 0).getClass();
        }
        return field.getType().getJavaType();
    }

    private Class<?> resolveElementType(String elementTypeName, Map<String, ModelDefinition> allDefinitions) {
        if (elementTypeName == null || elementTypeName.isEmpty()) {
            return Object.class;
        }
        
        Class<?> generatedClass = generatedClasses.get(elementTypeName);
        if (generatedClass != null) {
            return generatedClass;
        }
        
        ModelDefinition def = allDefinitions.get(elementTypeName);
        if (def != null) {
            try {
                return Class.forName(def.getFullName());
            } catch (ClassNotFoundException e) {
                // fall through
            }
        }
        
        try {
            return Class.forName("org.e4s.model." + elementTypeName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unknown element type: " + elementTypeName, e);
        }
    }

    private boolean isBucketModel(ModelDefinition definition) {
        return definition.getName().contains("Bucket") || definition.hasArrayField();
    }

    private DynamicType.Builder<?> addAllArgsConstructor(DynamicType.Builder<?> builder,
                                                           ModelDefinition definition,
                                                           Map<String, ModelDefinition> allDefinitions) {
        return builder;
    }

    private DynamicType.Builder<?> addGetter(DynamicType.Builder<?> builder, 
                                              FieldDefinition field,
                                              Map<String, ModelDefinition> allDefinitions) {
        Class<?> fieldType = resolveFieldType(field, allDefinitions);
        return builder.defineMethod(field.getGetterName(), fieldType, Modifier.PUBLIC)
                .intercept(FieldAccessor.ofField(field.getName()));
    }

    private DynamicType.Builder<?> addSetter(DynamicType.Builder<?> builder,
                                              FieldDefinition field,
                                              Map<String, ModelDefinition> allDefinitions) {
        Class<?> fieldType = resolveFieldType(field, allDefinitions);
        return builder.defineMethod(field.getSetterName(), void.class, Modifier.PUBLIC)
                .withParameter(fieldType, "value")
                .intercept(FieldAccessor.ofField(field.getName()));
    }

    private DynamicType.Builder<?> addToString(DynamicType.Builder<?> builder,
                                                ModelDefinition definition) {
        return builder.defineMethod("toString", String.class, Modifier.PUBLIC)
                .intercept(FixedValue.value(definition.getName() + "{}"));
    }

    private DynamicType.Builder<?> addBucketBusinessMethods(DynamicType.Builder<?> builder,
                                                             ModelDefinition definition,
                                                             Map<String, ModelDefinition> allDefinitions) {
        String arrayFieldName = definition.getArrayField().getName();
        String countFieldName = definition.getCountField() != null 
                ? definition.getCountField().getName() : null;
        String timestampField = definition.getTimestampField();
        Class<?> readingType = resolveElementType(definition.getArrayField().getElementType(), allDefinitions);
        
        BucketMethodContext context = new BucketMethodContext(
                arrayFieldName, countFieldName, timestampField, readingType
        );
        
        builder = addTouchMethod(builder);
        builder = addEnsureCapacityMethod(builder, context);
        builder = addAddReadingMethod(builder, context);
        builder = addAddReadingsMethod(builder, context);
        builder = addTrimToSizeMethod(builder, context);
        return builder;
    }

    private DynamicType.Builder<?> addTouchMethod(DynamicType.Builder<?> builder) {
        return builder.defineMethod("touch", void.class, Modifier.PUBLIC)
                .intercept(MethodDelegation.to(BucketInterceptors.TouchInterceptor.class));
    }

    private DynamicType.Builder<?> addAddReadingMethod(DynamicType.Builder<?> builder,
                                                        BucketMethodContext context) {
        return builder.defineMethod("addReading", void.class, Modifier.PUBLIC)
                .withParameter(context.readingType, "reading")
                .intercept(MethodDelegation.to(new BucketInterceptors.AddReadingInterceptor(context)));
    }

    private DynamicType.Builder<?> addAddReadingsMethod(DynamicType.Builder<?> builder,
                                                         BucketMethodContext context) {
        Class<?> arrayType = java.lang.reflect.Array.newInstance(context.readingType, 0).getClass();
        return builder.defineMethod("addReadings", void.class, Modifier.PUBLIC)
                .withParameter(arrayType, "newReadings")
                .intercept(MethodDelegation.to(new BucketInterceptors.AddReadingsInterceptor()));
    }

    private DynamicType.Builder<?> addEnsureCapacityMethod(DynamicType.Builder<?> builder,
                                                            BucketMethodContext context) {
        return builder.defineMethod("ensureCapacity", void.class, Modifier.PRIVATE)
                .withParameter(int.class, "minCapacity")
                .intercept(MethodDelegation.to(new BucketInterceptors.EnsureCapacityInterceptor(context)));
    }

    private DynamicType.Builder<?> addTrimToSizeMethod(DynamicType.Builder<?> builder,
                                                        BucketMethodContext context) {
        return builder.defineMethod("trimToSize", void.class, Modifier.PUBLIC)
                .intercept(MethodDelegation.to(new BucketInterceptors.TrimToSizeInterceptor(context)));
    }

    public static class BucketMethodContext {
        public final String arrayFieldName;
        public final String countFieldName;
        public final String timestampField;
        public final Class<?> readingType;

        public BucketMethodContext(String arrayFieldName, String countFieldName, 
                                   String timestampField, Class<?> readingType) {
            this.arrayFieldName = arrayFieldName;
            this.countFieldName = countFieldName;
            this.timestampField = timestampField;
            this.readingType = readingType;
        }
    }
}
