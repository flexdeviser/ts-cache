package org.e4s.server.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.Serializer;
import org.e4s.server.model.MeterReadingV2;

public class MeterReadingV2KryoSerializer extends Serializer<MeterReadingV2> {

    @Override
    public void write(Kryo kryo, Output output, MeterReadingV2 reading) {
        output.writeLong(reading.getReportedTs());
        output.writeDouble(reading.getVoltage());
        output.writeDouble(reading.getCurrent());
        output.writeDouble(reading.getPower());
    }

    @Override
    public MeterReadingV2 read(Kryo kryo, Input input, Class<? extends MeterReadingV2> type) {
        long reportedTs = input.readLong();
        double voltage = input.readDouble();
        double current = input.readDouble();
        double power = input.readDouble();
        return new MeterReadingV2(reportedTs, voltage, current, power);
    }

    @Override
    public boolean isImmutable() {
        return false;
    }
}
