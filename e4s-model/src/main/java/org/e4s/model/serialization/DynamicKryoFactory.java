package org.e4s.model.serialization;

import com.esotericsoftware.kryo.Kryo;
import org.e4s.model.MeterBucket;
import org.e4s.model.MeterReading;
import org.e4s.model.dynamic.DynamicModelRegistry;

public final class DynamicKryoFactory {

    private static final String MODELS_XML_PATH = "models.xml";
    private static volatile boolean initialized = false;

    private DynamicKryoFactory() {
    }

    public static synchronized void initialize() {
        if (!initialized) {
            DynamicModelRegistry.getInstance().initialize(MODELS_XML_PATH);
            initialized = true;
        }
    }

    public static Kryo createKryo() {
        ensureInitialized();
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        
        DynamicModelRegistry.getInstance().registerKryo(kryo);
        
        return kryo;
    }

    public static void register(Kryo kryo) {
        ensureInitialized();
        DynamicModelRegistry.getInstance().registerKryo(kryo);
    }

    public static Kryo createKryoWithFallback() {
        ensureInitialized();
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        
        kryo.register(MeterReading.class, new MeterReadingSerializer());
        kryo.register(MeterBucket.class, new MeterBucketSerializer());
        
        DynamicModelRegistry.getInstance().registerKryo(kryo);
        
        return kryo;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    public static boolean isDynamicMode() {
        return initialized && DynamicModelRegistry.getInstance().isInitialized();
    }
}
