package org.e4s.model.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.Serializer;
import org.e4s.model.MeterBucket;
import org.e4s.model.MeterReading;

/**
 * Kryo serializer for {@link MeterBucket} objects.
 * 
 * <p>This custom serializer optimizes storage by:
 * <ul>
 *   <li>Writing reading count instead of array length (avoids sparse array serialization)</li>
 *   <li>Serializing readings inline without array wrapper overhead</li>
 *   <li>Using primitive writes for timestamp and measurement values</li>
 * </ul>
 * 
 * <p>Binary format:
 * <pre>
 * | Field            | Type                    | Notes                        |
 * |------------------|-------------------------|------------------------------|
 * | meterId          | String (var len)        | Meter identifier             |
 * | bucketDate       | long                    | Epoch day                    |
 * | readingCount     | int                     | Number of readings           |
 * | readings[]       | MeterReading[count]     | Inline reading data (32b ea) |
 * | lastAccessTime   | long                    | For eviction decisions       |
 * | createdTime      | long                    | For eviction decisions       |
 * </pre>
 * 
 * <p>Typical size for 96 readings: ~3 KB uncompressed, ~1.5-2 KB after Deflater compression.
 */
public class MeterBucketSerializer extends Serializer<MeterBucket> {

    @Override
    public void write(Kryo kryo, Output output, MeterBucket bucket) {
        output.writeString(bucket.getMeterId());
        output.writeLong(bucket.getBucketDateEpochDay());
        output.writeInt(bucket.getReadingCount());

        MeterReading[] readings = bucket.getReadings();
        for (int i = 0; i < bucket.getReadingCount(); i++) {
            MeterReading reading = readings[i];
            output.writeLong(reading.getReportedTs());
            output.writeDouble(reading.getVoltage());
            output.writeDouble(reading.getCurrent());
            output.writeDouble(reading.getPower());
        }

        output.writeLong(bucket.getLastAccessTime());
        output.writeLong(bucket.getCreatedTime());
    }

    @Override
    public MeterBucket read(Kryo kryo, Input input, Class<? extends MeterBucket> type) {
        String meterId = input.readString();
        long bucketDateEpochDay = input.readLong();
        int readingCount = input.readInt();

        MeterBucket bucket = new MeterBucket(meterId, bucketDateEpochDay, readingCount);

        for (int i = 0; i < readingCount; i++) {
            long reportedTs = input.readLong();
            double voltage = input.readDouble();
            double current = input.readDouble();
            double power = input.readDouble();
            bucket.addReading(new MeterReading(reportedTs, voltage, current, power));
        }

        bucket.setLastAccessTime(input.readLong());
        bucket.setCreatedTime(input.readLong());

        return bucket;
    }

    @Override
    public boolean isImmutable() {
        return false;
    }
}
