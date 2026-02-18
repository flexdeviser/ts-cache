package org.e4s.server.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MeterDayKeyTest {

    @Test
    void testCreateKey() {
        LocalDate date = LocalDate.of(2026, 2, 18);
        MeterDayKey key = new MeterDayKey("MTR-001", date);

        assertEquals("MTR-001", key.getMeterId());
        assertEquals(date, key.getDay());
    }

    @Test
    void testStaticFactoryMethod() {
        LocalDate date = LocalDate.of(2026, 2, 18);
        MeterDayKey key = MeterDayKey.of("MTR-001", date);

        assertEquals("MTR-001", key.getMeterId());
        assertEquals(date, key.getDay());
    }

    @Test
    void testToKeyString() {
        LocalDate date = LocalDate.of(2026, 2, 18);
        MeterDayKey key = MeterDayKey.of("MTR-001", date);

        assertEquals("MTR-001:2026-02-18", key.toKeyString());
    }

    @Test
    void testParseKey() {
        MeterDayKey key = MeterDayKey.parse("MTR-001:2026-02-18");

        assertEquals("MTR-001", key.getMeterId());
        assertEquals(LocalDate.of(2026, 2, 18), key.getDay());
    }

    @Test
    void testParseInvalidKeyThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> MeterDayKey.parse("invalid-key"));
        assertThrows(IllegalArgumentException.class, () -> MeterDayKey.parse("MTR-001"));
    }

    @Test
    void testEqualsAndHashCode() {
        LocalDate date = LocalDate.of(2026, 2, 18);
        MeterDayKey key1 = MeterDayKey.of("MTR-001", date);
        MeterDayKey key2 = MeterDayKey.of("MTR-001", date);
        MeterDayKey key3 = MeterDayKey.of("MTR-002", date);
        MeterDayKey key4 = MeterDayKey.of("MTR-001", date.plusDays(1));

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, key3);
        assertNotEquals(key1, key4);
        assertNotEquals(key1, null);
        assertNotEquals(key1, "string");
    }

    @Test
    void testToString() {
        LocalDate date = LocalDate.of(2026, 2, 18);
        MeterDayKey key = MeterDayKey.of("MTR-001", date);

        assertEquals("MTR-001:2026-02-18", key.toString());
    }

    @Test
    void testSetters() {
        MeterDayKey key = new MeterDayKey();
        key.setMeterId("MTR-003");
        key.setDay(LocalDate.of(2026, 2, 19));

        assertEquals("MTR-003", key.getMeterId());
        assertEquals(LocalDate.of(2026, 2, 19), key.getDay());
    }
}
