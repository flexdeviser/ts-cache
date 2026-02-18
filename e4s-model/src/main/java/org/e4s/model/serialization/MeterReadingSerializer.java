package org.e4s.model.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.Serializer;
import org.e4s.model.MeterReading;

public class MeterReadingSerializer extends Serializer<MeterReading> {

    @Override
    public void write(Kryo kryo, Output output, MeterReading reading) {
        output.writeLong(reading.getReportedTs());
        output.writeDouble(reading.getVoltage());
        output.writeDouble(reading.getCurrent());
        output.writeDouble(reading.getPower());
    }

    @Override
    public MeterReading read(Kryo kryo, Input input, Class<? extends MeterReading> type) {
        long reportedTs = input.readLong();
        double voltage = input.readDouble();
        double current = input.readDouble();
        double power = input.readDouble();
        return new MeterReading(reportedTs, voltage, current, power);
    }

    @Override
    public boolean isImmutable() {
        return false;
    }
}
