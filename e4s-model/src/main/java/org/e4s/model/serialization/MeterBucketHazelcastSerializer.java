package org.e4s.model.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.e4s.model.MeterBucket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Hazelcast StreamSerializer for {@link MeterBucket} using Kryo + Deflater compression.
 * 
 * <p>This is the primary serializer for in-memory storage, providing ~90% memory reduction:
 * <ol>
 *   <li><b>Kryo serialization:</b> ~3 KB per bucket (vs ~20 KB with Java serialization)</li>
 *   <li><b>Deflater compression:</b> ~1.5-2 KB per bucket (additional ~50% reduction)</li>
 * </ol>
 * 
 * <p>Memory savings breakdown:
 * <pre>
 * Java serialization:  ~20 KB per bucket (96 readings)
 * Kryo only:          ~3 KB per bucket (85% reduction)
 * Kryo + Deflater:    ~1.5-2 KB per bucket (90%+ reduction)
 * </pre>
 * 
 * <p>The compression is particularly effective because:
 * <ul>
 *   <li>Time-series data has similar values that compress well</li>
 *   <li>Timestamps follow predictable patterns (15-minute intervals)</li>
 *   <li>Voltage/current/power values have limited variance within a day</li>
 * </ul>
 * 
 * <p>Thread safety: Uses ThreadLocal Kryo pool for thread-safe, lock-free operation.
 * 
 * <p>Important: Calls {@link MeterBucket#trimToSize()} before serialization to release
 * unused array capacity, ensuring minimal serialized size.
 * 
 * @see MeterReadingHazelcastSerializer
 * @see KryoFactory
 */
public class MeterBucketHazelcastSerializer implements StreamSerializer<MeterBucket> {

    public static final int TYPE_ID = 2002;

    private static final int COMPRESSION_LEVEL = 6;

    private static final ThreadLocal<Kryo> KRYO_POOL = ThreadLocal.withInitial(KryoFactory::createKryo);

    @Override
    public void write(ObjectDataOutput out, MeterBucket object) throws IOException {
        object.trimToSize();

        Deflater deflater = new Deflater(COMPRESSION_LEVEL);
        DeflaterOutputStream deflaterStream = new DeflaterOutputStream((OutputStream) out, deflater);
        Output kryoOutput = new Output(deflaterStream, 8192);

        KRYO_POOL.get().writeObject(kryoOutput, object);
        kryoOutput.flush();
        deflaterStream.finish();
        deflater.end();
    }

    @Override
    public MeterBucket read(ObjectDataInput in) throws IOException {
        InflaterInputStream inflaterStream = new InflaterInputStream((InputStream) in);
        Input kryoInput = new Input(inflaterStream, 8192);

        return KRYO_POOL.get().readObject(kryoInput, MeterBucket.class);
    }

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

    @Override
    public void destroy() {
    }
}
