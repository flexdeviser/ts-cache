package org.e4s.model.dynamic;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import org.e4s.model.GenericBucket;
import org.e4s.model.Timestamped;
import org.e4s.model.config.ModelConfig;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DynamicModelRegistry {

    private static final DynamicModelRegistry INSTANCE = new DynamicModelRegistry();
    
    private final Map<String, Class<?>> modelClasses = new HashMap<>();
    private final Map<Class<?>, String> classToModelName = new HashMap<>();
    private final Map<String, Serializer<?>> serializers = new HashMap<>();
    private final Map<String, ModelDefinition> definitions = new HashMap<>();
    private volatile boolean initialized = false;
    private volatile String modelsPath;
    private volatile String modelsHash;

    private DynamicModelRegistry() {
    }

    public static DynamicModelRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        initialize((String) null);
    }

    public synchronized void initialize(String modelsPath) {
        if (initialized) {
            return;
        }
        
        String resolvedPath = ModelConfig.resolveModelPath(modelsPath);
        this.modelsPath = resolvedPath;
        
        System.out.println(ModelConfig.getResolutionInfo(modelsPath));
        
        ModelDefinitionLoader loader = new ModelDefinitionLoader();
        List<ModelDefinition> modelDefinitions = loader.load(resolvedPath);
        
        Map<String, ModelDefinition> definitionMap = new HashMap<>();
        for (ModelDefinition def : modelDefinitions) {
            definitionMap.put(def.getName(), def);
        }
        this.definitions.putAll(definitionMap);
        
        ByteBuddyClassGenerator classGenerator = new ByteBuddyClassGenerator();
        Map<String, Class<?>> classes = classGenerator.generateAllClasses(definitionMap);
        this.modelClasses.putAll(classes);
        
        for (Map.Entry<String, Class<?>> entry : classes.entrySet()) {
            classToModelName.put(entry.getValue(), entry.getKey());
        }
        
        KryoSerializerGenerator serializerGenerator = new KryoSerializerGenerator(classes, definitionMap);
        Map<String, Serializer<?>> generatedSerializers = serializerGenerator.generateSerializers();
        this.serializers.putAll(generatedSerializers);
        
        this.modelsHash = ModelConfig.computeModelHash(resolvedPath);
        
        System.out.println("  Model hash: " + modelsHash.substring(0, 16) + "...");
        System.out.println("  Models loaded: " + definitions.keySet());
        
        initialized = true;
    }

    public Class<?> getClass(String modelName) {
        checkInitialized();
        return modelClasses.get(modelName);
    }

    @SuppressWarnings("unchecked")
    public Class<? extends Timestamped> getTimestampedClass(String modelName) {
        checkInitialized();
        Class<?> clazz = modelClasses.get(modelName);
        if (clazz != null && Timestamped.class.isAssignableFrom(clazz)) {
            return (Class<? extends Timestamped>) clazz;
        }
        return null;
    }

    public String getModelName(Class<?> clazz) {
        checkInitialized();
        return classToModelName.get(clazz);
    }

    public Serializer<?> getSerializer(String modelName) {
        checkInitialized();
        return serializers.get(modelName);
    }

    public ModelDefinition getDefinition(String modelName) {
        checkInitialized();
        return definitions.get(modelName);
    }

    public String getModelsPath() {
        checkInitialized();
        return modelsPath;
    }

    public String getModelsHash() {
        checkInitialized();
        return modelsHash;
    }

    public Set<String> getModelNames() {
        checkInitialized();
        return definitions.keySet();
    }

    public int getModelCount() {
        checkInitialized();
        return definitions.size();
    }

    public void registerKryo(Kryo kryo) {
        checkInitialized();
        
        for (Map.Entry<String, Class<?>> entry : modelClasses.entrySet()) {
            String modelName = entry.getKey();
            Class<?> modelClass = entry.getValue();
            Serializer<?> serializer = serializers.get(modelName);
            kryo.register(modelClass, serializer);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("DynamicModelRegistry has not been initialized. " +
                    "Call initialize() first.");
        }
    }

    public Object createInstance(String modelName) {
        checkInitialized();
        try {
            Class<?> clazz = modelClasses.get(modelName);
            if (clazz == null) {
                throw new IllegalArgumentException("Unknown model: " + modelName);
            }
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + modelName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public Timestamped createReading(String modelName, Map<String, Object> fieldValues) {
        checkInitialized();
        try {
            Object instance = createInstance(modelName);
            
            ModelDefinition modelDef = definitions.get(modelName);
            if (modelDef == null) {
                throw new IllegalArgumentException("Unknown model: " + modelName);
            }
            
            for (FieldDefinition field : modelDef.getFields()) {
                String fieldName = field.getName();
                if (fieldValues.containsKey(fieldName)) {
                    setFieldValue(instance, fieldName, fieldValues.get(fieldName));
                }
            }
            
            return (Timestamped) instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create reading " + modelName, e);
        }
    }

    public Object getFieldValue(Object object, String fieldName) {
        try {
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Method getter = object.getClass().getMethod(getterName);
            return getter.invoke(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field " + fieldName, e);
        }
    }

    public void setFieldValue(Object object, String fieldName, Object value) {
        try {
            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            ModelDefinition modelDef = findModelDefinition(object.getClass());
            if (modelDef == null) {
                throw new IllegalArgumentException("Unknown model class: " + object.getClass());
            }
            FieldDefinition fieldDef = modelDef.getFieldByName(fieldName);
            if (fieldDef == null) {
                throw new IllegalArgumentException("Unknown field: " + fieldName);
            }
            Method setter = object.getClass().getMethod(setterName, fieldDef.getType().getJavaType());
            setter.invoke(object, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    private ModelDefinition findModelDefinition(Class<?> clazz) {
        for (Map.Entry<String, Class<?>> entry : modelClasses.entrySet()) {
            if (entry.getValue() == clazz) {
                return definitions.get(entry.getKey());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public GenericBucket<Timestamped> createBucket(String modelName, String id, long bucketDateEpochDay) {
        checkInitialized();
        Class<? extends Timestamped> readingClass = getTimestampedClass(modelName);
        if (readingClass == null) {
            throw new IllegalArgumentException("Unknown reading model: " + modelName);
        }
        return new GenericBucket<>(id, bucketDateEpochDay, modelName, (Class<Timestamped>) readingClass);
    }

    public void validateHashMatch(String serverHash) {
        checkInitialized();
        
        if (serverHash == null || !serverHash.equals(this.modelsHash)) {
            String error = """

                ERROR: Model definition mismatch between client and server
                  Server model hash: %s
                  Client model hash: %s
                
                Ensure both client and server use the same models.xml file.
                Check that the models.xml path is configured correctly.
                """.formatted(
                    serverHash != null ? serverHash.substring(0, Math.min(16, serverHash.length())) + "..." : "(unknown)",
                    this.modelsHash.substring(0, 16) + "..."
                );
            throw new IllegalStateException(error);
        }
    }
}
