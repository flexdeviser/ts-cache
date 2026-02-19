package org.e4s.model.serialization;

import com.esotericsoftware.kryo.Kryo;
import org.e4s.model.dynamic.DynamicModelRegistry;

public final class KryoFactory {

    private KryoFactory() {
    }

    public static Kryo createKryo() {
        DynamicModelRegistry.getInstance().initialize("models.xml");
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        DynamicModelRegistry.getInstance().registerKryo(kryo);
        return kryo;
    }

    public static void register(Kryo kryo) {
        DynamicModelRegistry.getInstance().initialize("models.xml");
        DynamicModelRegistry.getInstance().registerKryo(kryo);
    }
}
