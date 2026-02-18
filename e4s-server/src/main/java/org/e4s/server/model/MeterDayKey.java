package org.e4s.server.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class MeterDayKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String meterId;
    private LocalDate day;

    public MeterDayKey() {
    }

    public MeterDayKey(String meterId, LocalDate day) {
        this.meterId = meterId;
        this.day = day;
    }

    public static MeterDayKey of(String meterId, LocalDate day) {
        return new MeterDayKey(meterId, day);
    }

    public static MeterDayKey parse(String key) {
        String[] parts = key.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid key format: " + key);
        }
        return new MeterDayKey(parts[0], LocalDate.parse(parts[1]));
    }

    public String getMeterId() {
        return meterId;
    }

    public void setMeterId(String meterId) {
        this.meterId = meterId;
    }

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public String toKeyString() {
        return meterId + ":" + day;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeterDayKey that = (MeterDayKey) o;
        return Objects.equals(meterId, that.meterId) && Objects.equals(day, that.day);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meterId, day);
    }

    @Override
    public String toString() {
        return toKeyString();
    }
}
