package org.e4s.model.dynamic;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DynamicModelRegistry {

    private static final DynamicModelRegistry INSTANCE = new DynamicModelRegistry();
    
    private final Map<String, Class<?>> modelClasses = new HashMap<>();
    private final Map<String, Serializer<?>> serializers = new HashMap<>();
    private final Map<String, ModelDefinition> definitions = new HashMap<>();
    private volatile boolean initialized = false;

    private DynamicModelRegistry() {
    }

    public static DynamicModelRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(String modelsXmlPath) {
        if (initialized) {
            return;
        }
        
        ModelDefinitionLoader loader = new ModelDefinitionLoader();
        List<ModelDefinition> modelDefinitions = loader.load(modelsXmlPath);
        
        Map<String, ModelDefinition> definitionMap = new HashMap<>();
        for (ModelDefinition def : modelDefinitions) {
            definitionMap.put(def.getName(), def);
        }
        this.definitions.putAll(definitionMap);
        
        ByteBuddyClassGenerator classGenerator = new ByteBuddyClassGenerator();
        Map<String, Class<?>> classes = classGenerator.generateAllClasses(definitionMap);
        this.modelClasses.putAll(classes);
        
        KryoSerializerGenerator serializerGenerator = new KryoSerializerGenerator(classes, definitionMap);
        Map<String, Serializer<?>> generatedSerializers = serializerGenerator.generateSerializers();
        this.serializers.putAll(generatedSerializers);
        
        initialized = true;
    }

    public Class<?> getClass(String modelName) {
        checkInitialized();
        return modelClasses.get(modelName);
    }

    public Serializer<?> getSerializer(String modelName) {
        checkInitialized();
        return serializers.get(modelName);
    }

    public ModelDefinition getDefinition(String modelName) {
        checkInitialized();
        return definitions.get(modelName);
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

    public Object createInstance(String modelName) throws Exception {
        checkInitialized();
        Class<?> clazz = modelClasses.get(modelName);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown model: " + modelName);
        }
        return clazz.getDeclaredConstructor().newInstance();
    }

    public Object createInstance(String modelName, Object... args) throws Exception {
        checkInitialized();
        Class<?> clazz = modelClasses.get(modelName);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown model: " + modelName);
        }
        
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }
        
        Constructor<?> ctor = findConstructor(clazz, paramTypes);
        return ctor.newInstance(args);
    }

    private Constructor<?> findConstructor(Class<?> clazz, Class<?>[] paramTypes) 
            throws NoSuchMethodException {
        for (Constructor<?> ctor : clazz.getConstructors()) {
            Class<?>[] ctorParamTypes = ctor.getParameterTypes();
            if (ctorParamTypes.length == paramTypes.length) {
                boolean match = true;
                for (int i = 0; i < paramTypes.length; i++) {
                    if (!ctorParamTypes[i].isAssignableFrom(paramTypes[i]) &&
                        !isAssignableWithPrimitives(ctorParamTypes[i], paramTypes[i])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return ctor;
                }
            }
        }
        throw new NoSuchMethodException("No suitable constructor found");
    }

    private boolean isAssignableWithPrimitives(Class<?> expected, Class<?> actual) {
        if (expected == long.class && actual == Long.class) return true;
        if (expected == int.class && actual == Integer.class) return true;
        if (expected == double.class && actual == Double.class) return true;
        if (expected == float.class && actual == Float.class) return true;
        if (expected == boolean.class && actual == Boolean.class) return true;
        return false;
    }
}
