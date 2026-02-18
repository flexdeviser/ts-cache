package org.e4s.server.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MeterReadingTest {

    @Test
    void testCreateMeterReading() {
        Instant now = Instant.now();
        MeterReading reading = new MeterReading(now, 
                new BigDecimal("220.5"), 
                new BigDecimal("5.2"), 
                new BigDecimal("1146.6"));

        assertEquals(now, reading.getReportedTs());
        assertEquals(new BigDecimal("220.5"), reading.getVoltage());
        assertEquals(new BigDecimal("5.2"), reading.getCurrent());
        assertEquals(new BigDecimal("1146.6"), reading.getPower());
    }

    @Test
    void testSettersAndGetters() {
        MeterReading reading = new MeterReading();
        Instant now = Instant.now();

        reading.setReportedTs(now);
        reading.setVoltage(new BigDecimal("230.0"));
        reading.setCurrent(new BigDecimal("6.0"));
        reading.setPower(new BigDecimal("1380.0"));

        assertEquals(now, reading.getReportedTs());
        assertEquals(new BigDecimal("230.0"), reading.getVoltage());
        assertEquals(new BigDecimal("6.0"), reading.getCurrent());
        assertEquals(new BigDecimal("1380.0"), reading.getPower());
    }

    @Test
    void testToString() {
        Instant now = Instant.parse("2026-02-18T10:00:00Z");
        MeterReading reading = new MeterReading(now, 
                new BigDecimal("220.5"), 
                new BigDecimal("5.2"), 
                new BigDecimal("1146.6"));

        String str = reading.toString();
        assertTrue(str.contains("2026-02-18T10:00:00Z"));
        assertTrue(str.contains("220.5"));
        assertTrue(str.contains("5.2"));
        assertTrue(str.contains("1146.6"));
    }
}
