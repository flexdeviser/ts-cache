package org.e4s.server.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.Serializer;
import org.e4s.server.model.MeterBucketV2;
import org.e4s.server.model.MeterReadingV2;

public class MeterBucketV2KryoSerializer extends Serializer<MeterBucketV2> {

    @Override
    public void write(Kryo kryo, Output output, MeterBucketV2 bucket) {
        output.writeString(bucket.getMeterId());
        output.writeLong(bucket.getBucketDateEpochDay());
        output.writeInt(bucket.getReadingCount());
        
        MeterReadingV2[] readings = bucket.getReadings();
        for (int i = 0; i < bucket.getReadingCount(); i++) {
            MeterReadingV2 reading = readings[i];
            output.writeLong(reading.getReportedTs());
            output.writeDouble(reading.getVoltage());
            output.writeDouble(reading.getCurrent());
            output.writeDouble(reading.getPower());
        }
        
        output.writeLong(bucket.getLastAccessTime());
        output.writeLong(bucket.getCreatedTime());
    }

    @Override
    public MeterBucketV2 read(Kryo kryo, Input input, Class<? extends MeterBucketV2> type) {
        String meterId = input.readString();
        long bucketDateEpochDay = input.readLong();
        int readingCount = input.readInt();
        
        MeterBucketV2 bucket = new MeterBucketV2(meterId, bucketDateEpochDay, readingCount);
        
        for (int i = 0; i < readingCount; i++) {
            long reportedTs = input.readLong();
            double voltage = input.readDouble();
            double current = input.readDouble();
            double power = input.readDouble();
            bucket.addReading(new MeterReadingV2(reportedTs, voltage, current, power));
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
