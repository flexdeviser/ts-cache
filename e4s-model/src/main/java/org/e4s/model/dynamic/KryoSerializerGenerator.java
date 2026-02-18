package org.e4s.model.dynamic;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.Serializer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        if (isBucketModel(definition)) {
            return new DynamicBucketSerializer(definition, modelClass, generatedClasses);
        } else {
            return new DynamicReadingSerializer(definition, modelClass);
        }
    }

    private boolean isBucketModel(ModelDefinition definition) {
        return definition.getName().contains("Bucket") || definition.hasArrayField();
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
        public void write(Kryo kryo, Output output, Object object) {
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
        public Object read(Kryo kryo, Input input, Class<? extends Object> type) {
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

    public static class DynamicBucketSerializer extends Serializer<Object> {
        private final ModelDefinition definition;
        private final Class<?> modelClass;
        private final Map<String, Class<?>> generatedClasses;
        private final Field[] fields;
        private final String arrayFieldName;
        private final String countFieldName;
        private final Class<?> elementType;

        public DynamicBucketSerializer(ModelDefinition definition, Class<?> modelClass,
                                        Map<String, Class<?>> generatedClasses) {
            this.definition = definition;
            this.modelClass = modelClass;
            this.generatedClasses = generatedClasses;
            this.fields = modelClass.getDeclaredFields();
            
            for (Field f : fields) {
                f.setAccessible(true);
            }
            
            FieldDefinition arrayField = definition.getArrayField();
            this.arrayFieldName = arrayField != null ? arrayField.getName() : null;
            
            FieldDefinition countField = definition.getCountField();
            this.countFieldName = countField != null ? countField.getName() : null;
            
            this.elementType = arrayField != null 
                    ? generatedClasses.get(arrayField.getElementType())
                    : Object.class;
        }

        @Override
        public void write(Kryo kryo, Output output, Object object) {
            try {
                int count = 0;
                
                for (Field field : fields) {
                    if (field.getName().equals(arrayFieldName)) {
                        continue;
                    }
                    
                    Object value = field.get(object);
                    
                    if (field.getName().equals(countFieldName)) {
                        count = (Integer) value;
                        output.writeInt(count);
                    } else if (field.getType() == long.class) {
                        output.writeLong((Long) value);
                    } else if (field.getType() == String.class) {
                        output.writeString((String) value);
                    } else if (field.getType() == int.class) {
                        output.writeInt((Integer) value);
                    }
                }
                
                if (arrayFieldName != null) {
                    Field arrayField = modelClass.getDeclaredField(arrayFieldName);
                    arrayField.setAccessible(true);
                    Object[] readings = (Object[]) arrayField.get(object);
                    
                    for (int i = 0; i < count; i++) {
                        writeReading(output, readings[i]);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize " + modelClass.getName(), e);
            }
        }

        private void writeReading(Output output, Object reading) throws Exception {
            for (Field f : reading.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object value = f.get(reading);
                
                if (f.getType() == long.class) {
                    output.writeLong((Long) value);
                } else if (f.getType() == double.class) {
                    output.writeDouble((Double) value);
                } else if (f.getType() == String.class) {
                    output.writeString((String) value);
                } else if (f.getType() == int.class) {
                    output.writeInt((Integer) value);
                }
            }
        }

        @Override
        public Object read(Kryo kryo, Input input, Class<? extends Object> type) {
            try {
                Constructor<?> ctor = modelClass.getConstructor();
                Object instance = ctor.newInstance();
                
                int count = 0;
                
                for (Field field : fields) {
                    if (field.getName().equals(arrayFieldName)) {
                        continue;
                    }
                    
                    Object value;
                    
                    if (field.getName().equals(countFieldName)) {
                        count = input.readInt();
                        value = count;
                    } else if (field.getType() == long.class) {
                        value = input.readLong();
                    } else if (field.getType() == String.class) {
                        value = input.readString();
                    } else if (field.getType() == int.class) {
                        value = input.readInt();
                    } else {
                        continue;
                    }
                    
                    field.set(instance, value);
                }
                
                if (arrayFieldName != null && count > 0) {
                    Object[] readings = (Object[]) java.lang.reflect.Array.newInstance(elementType, count);
                    
                    for (int i = 0; i < count; i++) {
                        readings[i] = readReading(input);
                    }
                    
                    Field arrayField = modelClass.getDeclaredField(arrayFieldName);
                    arrayField.setAccessible(true);
                    arrayField.set(instance, readings);
                }
                
                return instance;
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize " + modelClass.getName(), e);
            }
        }

        private Object readReading(Input input) throws Exception {
            Constructor<?> ctor = elementType.getConstructor();
            Object instance = ctor.newInstance();
            
            for (Field f : elementType.getDeclaredFields()) {
                f.setAccessible(true);
                
                Object value;
                if (f.getType() == long.class) {
                    value = input.readLong();
                } else if (f.getType() == double.class) {
                    value = input.readDouble();
                } else if (f.getType() == String.class) {
                    value = input.readString();
                } else if (f.getType() == int.class) {
                    value = input.readInt();
                } else {
                    continue;
                }
                
                f.set(instance, value);
            }
            
            return instance;
        }
    }
}
