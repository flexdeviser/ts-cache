package org.e4s.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Composite key for identifying a daily meter bucket in the cache.
 * 
 * <p>The key format is "meterId:YYYY-MM-DD" (e.g., "MTR-001:2026-02-18").
 * This design enables:
 * <ul>
 *   <li>Efficient prefix scanning for time-range queries on a single meter</li>
 *   <li>Natural partitioning by day for eviction operations</li>
 *   <li>Human-readable keys for debugging and monitoring</li>
 * </ul>
 * 
 * <p>Key storage optimization:
 * <ul>
 *   <li>Stores day as epoch day (long) instead of LocalDate object</li>
 *   <li>Epoch day uses 8 bytes vs ~24 bytes for LocalDate</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * MeterDayKey key = MeterDayKey.of("MTR-001", LocalDate.of(2026, 2, 18));
 * String keyString = key.toKeyString(); // "MTR-001:2026-02-18"
 * 
 * // Parse from string
 * MeterDayKey parsed = MeterDayKey.parse("MTR-001:2026-02-18");
 * }</pre>
 * 
 * @see MeterBucket
 */
public class MeterDayKey {

    private String meterId;
    private long dayEpochDay;

    public MeterDayKey() {
    }

    public MeterDayKey(String meterId, long dayEpochDay) {
        this.meterId = meterId;
        this.dayEpochDay = dayEpochDay;
    }

    public MeterDayKey(String meterId, LocalDate day) {
        this.meterId = meterId;
        this.dayEpochDay = day.toEpochDay();
    }

    public static MeterDayKey of(String meterId, LocalDate day) {
        return new MeterDayKey(meterId, day);
    }

    public static MeterDayKey parse(String key) {
        String[] parts = key.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid key format: " + key);
        }
        return new MeterDayKey(parts[0], LocalDate.parse(parts[1]).toEpochDay());
    }

    public String getMeterId() {
        return meterId;
    }

    public void setMeterId(String meterId) {
        this.meterId = meterId;
    }

    public long getDayEpochDay() {
        return dayEpochDay;
    }

    public void setDayEpochDay(long dayEpochDay) {
        this.dayEpochDay = dayEpochDay;
    }

    public LocalDate getDay() {
        return LocalDate.ofEpochDay(dayEpochDay);
    }

    public String toKeyString() {
        return meterId + ":" + getDay().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeterDayKey that = (MeterDayKey) o;
        return dayEpochDay == that.dayEpochDay && Objects.equals(meterId, that.meterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meterId, dayEpochDay);
    }

    @Override
    public String toString() {
        return toKeyString();
    }
}
