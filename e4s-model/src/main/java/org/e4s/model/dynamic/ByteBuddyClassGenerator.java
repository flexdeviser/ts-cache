package org.e4s.model.dynamic;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
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

        List<DynamicType.Unloaded<?>> unloadedTypes = new ArrayList<>();
        Map<String, Integer> typeIndexMap = new HashMap<>();

        int index = 0;
        for (ModelDefinition def : definitions.values()) {
            DynamicType.Unloaded<?> unloaded = generateUnloadedClass(def, definitions);
            unloadedTypes.add(unloaded);
            typeIndexMap.put(def.getName(), index);
            index++;
        }

        Map<String, Class<?>> loaded = loadAllClasses(unloadedTypes, typeIndexMap, definitions);

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
        ByteBuddy byteBuddy = new ByteBuddy();
        
        DynamicType.Builder<?> builder;
        
        if (definition.getImplementsInterface() != null && !definition.getImplementsInterface().isEmpty()) {
            Class<?> interfaceClass = resolveInterface(definition.getImplementsInterface());
            builder = byteBuddy
                    .subclass(Object.class)
                    .name(definition.getFullName())
                    .modifiers(Modifier.PUBLIC)
                    .implement(interfaceClass);
        } else {
            builder = byteBuddy
                    .subclass(Object.class)
                    .name(definition.getFullName())
                    .modifiers(Modifier.PUBLIC);
        }

        for (FieldDefinition field : definition.getFields()) {
            Class<?> fieldType = resolveFieldType(field, allDefinitions);
            builder = builder.defineField(field.getName(), fieldType, Modifier.PRIVATE);
        }

        for (FieldDefinition field : definition.getFields()) {
            builder = addGetter(builder, field, allDefinitions);
            builder = addSetter(builder, field, allDefinitions);
        }

        if (definition.getTimestampField() != null) {
            builder = addGetTimestampMethod(builder, definition);
        }

        builder = addToString(builder, definition);

        return builder.make();
    }

    private Class<?> resolveInterface(String interfaceName) {
        if ("Timestamped".equals(interfaceName)) {
            return org.e4s.model.Timestamped.class;
        }
        try {
            return Class.forName(interfaceName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unknown interface: " + interfaceName, e);
        }
    }

    private DynamicType.Builder<?> addGetTimestampMethod(DynamicType.Builder<?> builder,
                                                          ModelDefinition definition) {
        String timestampField = definition.getTimestampField();
        return builder.defineMethod("getTimestamp", long.class, Modifier.PUBLIC)
                .intercept(FieldAccessor.ofField(timestampField));
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
}
