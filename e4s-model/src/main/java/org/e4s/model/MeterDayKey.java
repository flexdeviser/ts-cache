package org.e4s.model;

import java.time.LocalDate;
import java.util.Objects;

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
