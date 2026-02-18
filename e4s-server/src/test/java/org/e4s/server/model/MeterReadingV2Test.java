package org.e4s.server.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MeterReadingV2Test {

    @Test
    void testCreateMeterReadingV2() {
        long now = System.currentTimeMillis();
        MeterReadingV2 reading = new MeterReadingV2(now, 220.5, 5.2, 1146.6);

        assertEquals(now, reading.getReportedTs());
        assertEquals(220.5, reading.getVoltage(), 0.001);
        assertEquals(5.2, reading.getCurrent(), 0.001);
        assertEquals(1146.6, reading.getPower(), 0.001);
    }

    @Test
    void testSettersAndGetters() {
        MeterReadingV2 reading = new MeterReadingV2();
        long now = System.currentTimeMillis();

        reading.setReportedTs(now);
        reading.setVoltage(230.0);
        reading.setCurrent(6.0);
        reading.setPower(1380.0);

        assertEquals(now, reading.getReportedTs());
        assertEquals(230.0, reading.getVoltage(), 0.001);
        assertEquals(6.0, reading.getCurrent(), 0.001);
        assertEquals(1380.0, reading.getPower(), 0.001);
    }

    @Test
    void testToString() {
        long ts = Instant.parse("2026-02-18T10:00:00Z").toEpochMilli();
        MeterReadingV2 reading = new MeterReadingV2(ts, 220.5, 5.2, 1146.6);

        String str = reading.toString();
        assertTrue(str.contains("reportedTs="));
        assertTrue(str.contains("220.5"));
        assertTrue(str.contains("5.2"));
        assertTrue(str.contains("1146.6"));
    }

    @Test
    void testPrimitiveSize() {
        MeterReadingV2 reading = new MeterReadingV2();
        
        assertTrue(reading.getVoltage() == 0.0);
        assertTrue(reading.getCurrent() == 0.0);
        assertTrue(reading.getPower() == 0.0);
        assertTrue(reading.getReportedTs() == 0L);
    }
}
