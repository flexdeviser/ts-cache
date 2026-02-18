package org.e4s.server.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public class MeterReading implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Instant reportedTs;
    private BigDecimal voltage;
    private BigDecimal current;
    private BigDecimal power;

    public MeterReading() {
    }

    public MeterReading(Instant reportedTs, BigDecimal voltage, BigDecimal current, BigDecimal power) {
        this.reportedTs = reportedTs;
        this.voltage = voltage;
        this.current = current;
        this.power = power;
    }

    public Instant getReportedTs() {
        return reportedTs;
    }

    public void setReportedTs(Instant reportedTs) {
        this.reportedTs = reportedTs;
    }

    public BigDecimal getVoltage() {
        return voltage;
    }

    public void setVoltage(BigDecimal voltage) {
        this.voltage = voltage;
    }

    public BigDecimal getCurrent() {
        return current;
    }

    public void setCurrent(BigDecimal current) {
        this.current = current;
    }

    public BigDecimal getPower() {
        return power;
    }

    public void setPower(BigDecimal power) {
        this.power = power;
    }

    @Override
    public String toString() {
        return "MeterReading{" +
                "reportedTs=" + reportedTs +
                ", voltage=" + voltage +
                ", current=" + current +
                ", power=" + power +
                '}';
    }
}
