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
