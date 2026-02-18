package org.e4s.model.dynamic;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class KryoSerializerGenerator {

    private final Map<String, Class<?>> generatedClasses;
    private final Map<String, ModelDefinition> definitions;

    public KryoSerializerGenerator(Map<String, Class<?>> generatedClasses, 
                                    Map<String, ModelDefinition> definitions) {
        this.generatedClasses = generatedClasses;
        this.definitions = definitions;
    }

    public Map<String, Serializer<?>> generateSerializers() {
        Map<String, Serializer<?>> serializers = new HashMap<>();
        
        for (Map.Entry<String, Class<?>> entry : generatedClasses.entrySet()) {
            String modelName = entry.getKey();
            Class<?> modelClass = entry.getValue();
            ModelDefinition definition = definitions.get(modelName);
            
            Serializer<?> serializer = createSerializer(definition, modelClass);
            serializers.put(modelName, serializer);
        }
        
        return serializers;
    }

    public Serializer<?> createSerializer(ModelDefinition definition, Class<?> modelClass) {
        return new DynamicReadingSerializer(definition, modelClass);
    }

    public static class DynamicReadingSerializer extends Serializer<Object> {
        private final ModelDefinition definition;
        private final Class<?> modelClass;
        private final Field[] fields;

        public DynamicReadingSerializer(ModelDefinition definition, Class<?> modelClass) {
            this.definition = definition;
            this.modelClass = modelClass;
            this.fields = modelClass.getDeclaredFields();
            for (Field f : fields) {
                f.setAccessible(true);
            }
        }

        @Override
        public void write(com.esotericsoftware.kryo.Kryo kryo, 
                          com.esotericsoftware.kryo.io.Output output, Object object) {
            try {
                for (Field field : fields) {
                    Object value = field.get(object);
                    FieldType type = FieldType.fromString(getFieldTypeSimpleName(field));
                    
                    switch (type) {
                        case LONG -> output.writeLong((Long) value);
                        case INT -> output.writeInt((Integer) value);
                        case DOUBLE -> output.writeDouble((Double) value);
                        case FLOAT -> output.writeFloat((Float) value);
                        case BOOLEAN -> output.writeBoolean((Boolean) value);
                        case STRING -> output.writeString((String) value);
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to serialize " + modelClass.getName(), e);
            }
        }

        @Override
        public Object read(com.esotericsoftware.kryo.Kryo kryo, 
                           com.esotericsoftware.kryo.io.Input input, Class<? extends Object> type) {
            try {
                Constructor<?> ctor = modelClass.getConstructor();
                Object instance = ctor.newInstance();
                
                for (Field field : fields) {
                    FieldType fieldType = FieldType.fromString(getFieldTypeSimpleName(field));
                    Object value = switch (fieldType) {
                        case LONG -> input.readLong();
                        case INT -> input.readInt();
                        case DOUBLE -> input.readDouble();
                        case FLOAT -> input.readFloat();
                        case BOOLEAN -> input.readBoolean();
                        case STRING -> input.readString();
                        default -> throw new IllegalArgumentException("Unsupported field type: " + fieldType);
                    };
                    field.set(instance, value);
                }
                
                return instance;
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize " + modelClass.getName(), e);
            }
        }

        private String getFieldTypeSimpleName(Field field) {
            Class<?> type = field.getType();
            if (type == long.class) return "long";
            if (type == int.class) return "int";
            if (type == double.class) return "double";
            if (type == float.class) return "float";
            if (type == boolean.class) return "boolean";
            if (type == String.class) return "string";
            return type.getSimpleName().toLowerCase();
        }
    }
}
