package org.e4s.model.serialization;

import com.esotericsoftware.kryo.Kryo;
import org.e4s.model.MeterBucket;
import org.e4s.model.MeterReading;

/**
 * Factory for creating and configuring Kryo instances for meter data serialization.
 * 
 * <p>This factory ensures consistent Kryo configuration across the application:
 * <ul>
 *   <li>Registers custom serializers for {@link MeterReading} and {@link MeterBucket}</li>
 *   <li>Disables reference tracking for better performance (our objects don't have cycles)</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>{@code
 * Kryo kryo = KryoFactory.createKryo();
 * // or register to an existing instance
 * KryoFactory.register(existingKryo);
 * }</pre>
 * 
 * @see MeterReadingSerializer
 * @see MeterBucketSerializer
 */
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
