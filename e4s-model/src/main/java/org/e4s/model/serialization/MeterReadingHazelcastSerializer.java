package org.e4s.model.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.e4s.model.MeterReading;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Hazelcast StreamSerializer for {@link MeterReading} using Kryo + Deflater compression.
 * 
 * <p>This serializer combines two optimization techniques:
 * <ol>
 *   <li><b>Kryo serialization:</b> Efficient binary format (32 bytes per reading)</li>
 *   <li><b>Deflater compression:</b> ZLIB compression for additional size reduction</li>
 * </ol>
 * 
 * <p>Compression configuration:
 * <ul>
 *   <li>Level 6: Balanced between compression ratio and CPU usage</li>
 *   <li>Typical compression ratio: ~50% for time-series data (similar values compress well)</li>
 * </ul>
 * 
 * <p>Thread safety: Uses ThreadLocal Kryo pool for thread-safe, lock-free operation.
 * Each thread gets its own Kryo instance, avoiding synchronization overhead.
 * 
 * <p>Note: For single readings, compression overhead may exceed benefits.
 * This serializer is primarily used for consistency with bucket serialization.
 * 
 * @see MeterBucketHazelcastSerializer
 * @see KryoFactory
 */
public class MeterReadingHazelcastSerializer implements StreamSerializer<MeterReading> {

    public static final int TYPE_ID = 2001;

    private static final int COMPRESSION_LEVEL = 6;

    private static final ThreadLocal<Kryo> KRYO_POOL = ThreadLocal.withInitial(KryoFactory::createKryo);

    @Override
    public void write(ObjectDataOutput out, MeterReading object) throws IOException {
        Deflater deflater = new Deflater(COMPRESSION_LEVEL);
        DeflaterOutputStream deflaterStream = new DeflaterOutputStream((OutputStream) out, deflater);
        Output kryoOutput = new Output(deflaterStream, 512);

        KRYO_POOL.get().writeObject(kryoOutput, object);
        kryoOutput.flush();
        deflaterStream.finish();
        deflater.end();
    }

    @Override
    public MeterReading read(ObjectDataInput in) throws IOException {
        InflaterInputStream inflaterStream = new InflaterInputStream((InputStream) in);
        Input kryoInput = new Input(inflaterStream, 512);

        return KRYO_POOL.get().readObject(kryoInput, MeterReading.class);
    }

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

    @Override
    public void destroy() {
    }
}
