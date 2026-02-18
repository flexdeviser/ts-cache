package org.e4s.server.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.e4s.server.model.MeterBucketV2;
import org.e4s.server.model.MeterReadingV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.*;

class SerializationTest {

    private Kryo kryo;

    @BeforeEach
    void setUp() {
        kryo = new Kryo();
        kryo.register(MeterReadingV2.class, new MeterReadingV2KryoSerializer());
        kryo.register(MeterBucketV2.class, new MeterBucketV2KryoSerializer());
        kryo.setReferences(false);
    }

    @Test
    void testMeterReadingV2KryoSerializer() {
        MeterReadingV2 original = new MeterReadingV2(
                System.currentTimeMillis(),
                220.5,
                5.2,
                1146.6
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeObject(output, original);
        output.flush();

        byte[] bytes = baos.toByteArray();
        assertTrue(bytes.length > 0, "Serialized bytes should not be empty");

        Input input = new Input(new ByteArrayInputStream(bytes));
        MeterReadingV2 deserialized = kryo.readObject(input, MeterReadingV2.class);

        assertEquals(original.getReportedTs(), deserialized.getReportedTs());
        assertEquals(original.getVoltage(), deserialized.getVoltage(), 0.001);
        assertEquals(original.getCurrent(), deserialized.getCurrent(), 0.001);
        assertEquals(original.getPower(), deserialized.getPower(), 0.001);
    }

    @Test
    void testMeterBucketV2KryoSerializer() {
        long now = System.currentTimeMillis();
        MeterBucketV2 original = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay());
        original.addReading(new MeterReadingV2(now, 220.5, 5.2, 1146.6));
        original.addReading(new MeterReadingV2(now + 900000, 221.0, 5.3, 1171.3));
        original.addReading(new MeterReadingV2(now + 1800000, 222.0, 5.4, 1198.8));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeObject(output, original);
        output.flush();

        byte[] bytes = baos.toByteArray();
        assertTrue(bytes.length > 0, "Serialized bytes should not be empty");

        Input input = new Input(new ByteArrayInputStream(bytes));
        MeterBucketV2 deserialized = kryo.readObject(input, MeterBucketV2.class);

        assertEquals(original.getMeterId(), deserialized.getMeterId());
        assertEquals(original.getBucketDateEpochDay(), deserialized.getBucketDateEpochDay());
        assertEquals(original.getReadingCount(), deserialized.getReadingCount());

        MeterReadingV2[] originalReadings = original.getReadings();
        MeterReadingV2[] deserializedReadings = deserialized.getReadings();

        for (int i = 0; i < original.getReadingCount(); i++) {
            assertEquals(originalReadings[i].getReportedTs(), deserializedReadings[i].getReportedTs());
            assertEquals(originalReadings[i].getVoltage(), deserializedReadings[i].getVoltage(), 0.001);
            assertEquals(originalReadings[i].getCurrent(), deserializedReadings[i].getCurrent(), 0.001);
            assertEquals(originalReadings[i].getPower(), deserializedReadings[i].getPower(), 0.001);
        }
    }

    @Test
    void testMeterBucketV2With96Readings() {
        long base = System.currentTimeMillis();
        MeterBucketV2 original = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay());

        for (int i = 0; i < 96; i++) {
            original.addReading(new MeterReadingV2(
                    base + i * 15 * 60 * 1000,
                    220.0 + Math.random() * 10,
                    5.0 + Math.random() * 2,
                    1000.0 + Math.random() * 500
            ));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeObject(output, original);
        output.flush();

        byte[] bytes = baos.toByteArray();

        Input input = new Input(new ByteArrayInputStream(bytes));
        MeterBucketV2 deserialized = kryo.readObject(input, MeterBucketV2.class);

        assertEquals(96, deserialized.getReadingCount());
        assertEquals(96, deserialized.getReadings().length);
    }

    @Test
    void testMeterReadingSize() {
        MeterReadingV2 reading = new MeterReadingV2(System.currentTimeMillis(), 220.5, 5.2, 1146.6);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeObject(output, reading);
        output.flush();

        int size = baos.toByteArray().length;
        System.out.println("MeterReadingV2 serialized size: " + size + " bytes");

        assertTrue(size < 40, "Reading should be less than 40 bytes, was: " + size);
    }

    @Test
    void testMeterBucketWith96ReadingsSize() {
        long base = System.currentTimeMillis();
        MeterBucketV2 bucket = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay());

        for (int i = 0; i < 96; i++) {
            bucket.addReading(new MeterReadingV2(
                    base + i * 15 * 60 * 1000,
                    220.5,
                    5.2,
                    1146.6
            ));
        }

        bucket.trimToSize();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeObject(output, bucket);
        output.flush();

        int size = baos.toByteArray().length;
        System.out.println("MeterBucketV2 (96 readings) Kryo size: " + size + " bytes");
        System.out.println("Average per reading: " + (size / 96.0) + " bytes");

        assertTrue(size < 4000, "Bucket should be less than 4KB, was: " + size + " bytes");
    }

    @Test
    void testEmptyBucketSerialization() {
        MeterBucketV2 original = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        kryo.writeObject(output, original);
        output.flush();

        byte[] bytes = baos.toByteArray();

        Input input = new Input(new ByteArrayInputStream(bytes));
        MeterBucketV2 deserialized = kryo.readObject(input, MeterBucketV2.class);

        assertEquals(original.getMeterId(), deserialized.getMeterId());
        assertEquals(0, deserialized.getReadingCount());
    }

    @Test
    void testCompressionWithDeflater() throws IOException {
        long base = System.currentTimeMillis();
        MeterBucketV2 bucket = new MeterBucketV2("MTR-001", LocalDate.of(2026, 2, 18).toEpochDay());

        for (int i = 0; i < 96; i++) {
            bucket.addReading(new MeterReadingV2(
                    base + i * 15 * 60 * 1000,
                    220.5,
                    5.2,
                    1146.6
            ));
        }

        bucket.trimToSize();

        ByteArrayOutputStream uncompressedBaos = new ByteArrayOutputStream();
        Output uncompressedOutput = new Output(uncompressedBaos);
        kryo.writeObject(uncompressedOutput, bucket);
        uncompressedOutput.flush();
        int uncompressedSize = uncompressedBaos.toByteArray().length;

        ByteArrayOutputStream compressedBaos = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(6);
        DeflaterOutputStream deflaterStream = new DeflaterOutputStream(compressedBaos, deflater);
        Output compressedOutput = new Output(deflaterStream);
        kryo.writeObject(compressedOutput, bucket);
        compressedOutput.flush();
        deflaterStream.finish();
        deflater.end();
        int compressedSize = compressedBaos.toByteArray().length;

        System.out.println("Uncompressed size: " + uncompressedSize + " bytes");
        System.out.println("Compressed size (Deflater level 6): " + compressedSize + " bytes");
        System.out.println("Compression ratio: " + (100.0 * (uncompressedSize - compressedSize) / uncompressedSize) + "%");

        assertTrue(compressedSize < uncompressedSize, 
                "Compressed size should be less than uncompressed");
    }
}
