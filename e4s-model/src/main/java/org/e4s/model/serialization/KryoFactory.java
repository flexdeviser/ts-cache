package org.e4s.model.serialization;

import com.esotericsoftware.kryo.Kryo;
import org.e4s.model.MeterBucket;
import org.e4s.model.MeterReading;

public final class KryoFactory {

    private KryoFactory() {
    }

    public static Kryo createKryo() {
        Kryo kryo = new Kryo();
        kryo.register(MeterReading.class, new MeterReadingSerializer());
        kryo.register(MeterBucket.class, new MeterBucketSerializer());
        kryo.setReferences(false);
        return kryo;
    }

    public static void register(Kryo kryo) {
        kryo.register(MeterReading.class, new MeterReadingSerializer());
        kryo.register(MeterBucket.class, new MeterBucketSerializer());
    }
}
