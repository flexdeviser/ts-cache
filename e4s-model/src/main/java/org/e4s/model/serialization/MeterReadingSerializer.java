package org.e4s.model.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.Serializer;
import org.e4s.model.MeterReading;

/**
 * Kryo serializer for {@link MeterReading} objects.
 * 
 * <p>This custom serializer provides optimal binary representation by:
 * <ul>
 *   <li>Writing fields directly without field names (saves ~20 bytes per field)</li>
 *   <li>Using primitive write methods for long and double types</li>
 *   <li>Total serialized size: 32 bytes (8+8+8+8)</li>
 * </ul>
 * 
 * <p>Binary format (32 bytes total):
 * <pre>
 * | Offset | Type   | Field      |
 * |--------|--------|------------|
 * | 0-7    | long   | reportedTs |
 * | 8-15   | double | voltage    |
 * | 16-23  | double | current    |
 * | 24-31  | double | power      |
 * </pre>
 * 
 * <p>This is significantly smaller than Java's default serialization (~200 bytes)
 * or Kryo's default field serialization (~80 bytes with field names).
 */
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
