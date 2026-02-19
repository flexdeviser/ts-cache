package org.e4s.model.serialization;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.e4s.model.GenericBucket;
import org.e4s.model.Timestamped;
import org.e4s.model.dynamic.DynamicModelRegistry;
import org.e4s.model.dynamic.FieldDefinition;
import org.e4s.model.dynamic.FieldType;
import org.e4s.model.dynamic.ModelDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class GenericBucketHazelcastSerializer implements StreamSerializer<GenericBucket<Timestamped>> {

    public static final int TYPE_ID = 2003;

    private static final int COMPRESSION_LEVEL = 6;

    @Override
    public void write(ObjectDataOutput out, GenericBucket<Timestamped> object) throws IOException {
        ensureInitialized();
        
        object.trimToSize();

        Deflater deflater = new Deflater(COMPRESSION_LEVEL);
        DeflaterOutputStream deflaterStream = new DeflaterOutputStream((OutputStream) out, deflater);
        Output kryoOutput = new Output(deflaterStream, 8192);

        kryoOutput.writeString(object.getId());
        kryoOutput.writeLong(object.getBucketDateEpochDay());
        kryoOutput.writeString(object.getModelName());
        kryoOutput.writeInt(object.getReadingCount());
        kryoOutput.writeLong(object.getLastAccessTime());
        kryoOutput.writeLong(object.getCreatedTime());

        String modelName = object.getModelName();
        ModelDefinition modelDef = DynamicModelRegistry.getInstance().getDefinition(modelName);
        kryoOutput.writeString(modelDef.getName());
        
        List<FieldDefinition> fields = modelDef.getFields();
        kryoOutput.writeInt(fields.size());
        for (FieldDefinition field : fields) {
            kryoOutput.writeString(field.getName());
            kryoOutput.writeString(field.getType().name());
        }
        
        Timestamped[] readings = object.getReadings();
        int count = object.getReadingCount();
        
        for (int i = 0; i < count; i++) {
            Timestamped r = readings[i];
            writeReadingFields(kryoOutput, r, fields);
        }

        kryoOutput.flush();
        deflaterStream.finish();
        deflater.end();
    }

    private void writeReadingFields(Output output, Timestamped reading, List<FieldDefinition> fields) {
        try {
            Class<?> readingClass = reading.getClass();
            for (FieldDefinition field : fields) {
                String getterName = field.getGetterName();
                Method getter = readingClass.getMethod(getterName);
                Object value = getter.invoke(reading);
                
                FieldType type = field.getType();
                switch (type) {
                    case LONG -> output.writeLong(value != null ? (Long) value : 0L);
                    case INT -> output.writeInt(value != null ? (Integer) value : 0);
                    case DOUBLE -> output.writeDouble(value != null ? (Double) value : 0.0);
                    case FLOAT -> output.writeFloat(value != null ? (Float) value : 0.0f);
                    case BOOLEAN -> output.writeBoolean(value != null ? (Boolean) value : false);
                    case STRING -> output.writeString(value != null ? (String) value : null);
                    default -> throw new IOException("Unsupported field type: " + type);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to write reading fields", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public GenericBucket<Timestamped> read(ObjectDataInput in) throws IOException {
        ensureInitialized();
        
        InflaterInputStream inflaterStream = new InflaterInputStream((InputStream) in);
        Input kryoInput = new Input(inflaterStream, 8192);

        String id = kryoInput.readString();
        long bucketDateEpochDay = kryoInput.readLong();
        String modelName = kryoInput.readString();
        int readingCount = kryoInput.readInt();
        long lastAccessTime = kryoInput.readLong();
        long createdTime = kryoInput.readLong();

        String storedModelName = kryoInput.readString();
        int fieldCount = kryoInput.readInt();
        
        String[] fieldNames = new String[fieldCount];
        FieldType[] fieldTypes = new FieldType[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            fieldNames[i] = kryoInput.readString();
            fieldTypes[i] = FieldType.valueOf(kryoInput.readString());
        }

        GenericBucket<Timestamped> bucket = DynamicModelRegistry.getInstance()
                .createBucket(modelName, id, bucketDateEpochDay);
        
        for (int i = 0; i < readingCount; i++) {
            Timestamped reading = readReading(kryoInput, modelName, fieldNames, fieldTypes);
            bucket.addReading(reading);
        }
        
        bucket.setLastAccessTime(lastAccessTime);
        bucket.setCreatedTime(createdTime);

        return bucket;
    }

    private Timestamped readReading(Input input, String modelName, String[] fieldNames, FieldType[] fieldTypes) {
        try {
            Object instance = DynamicModelRegistry.getInstance().createInstance(modelName);
            Class<?> readingClass = instance.getClass();
            
            Object[] values = new Object[fieldNames.length];
            for (int i = 0; i < fieldNames.length; i++) {
                FieldType type = fieldTypes[i];
                values[i] = switch (type) {
                    case LONG -> input.readLong();
                    case INT -> input.readInt();
                    case DOUBLE -> input.readDouble();
                    case FLOAT -> input.readFloat();
                    case BOOLEAN -> input.readBoolean();
                    case STRING -> input.readString();
                    default -> throw new IOException("Unsupported field type: " + type);
                };
            }
            
            for (int i = 0; i < fieldNames.length; i++) {
                String setterName = "set" + Character.toUpperCase(fieldNames[i].charAt(0)) + fieldNames[i].substring(1);
                Method setter = readingClass.getMethod(setterName, fieldTypes[i].getJavaType());
                setter.invoke(instance, values[i]);
            }
            
            return (Timestamped) instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read reading", e);
        }
    }

    private void ensureInitialized() {
        if (!DynamicModelRegistry.getInstance().isInitialized()) {
            DynamicModelRegistry.getInstance().initialize();
        }
    }

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

    @Override
    public void destroy() {
    }
}
