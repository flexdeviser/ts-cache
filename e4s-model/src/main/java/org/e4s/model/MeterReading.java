package org.e4s.model;

public class MeterReading {

    private long reportedTs;
    private double voltage;
    private double current;
    private double power;

    public MeterReading() {
    }

    public MeterReading(long reportedTs, double voltage, double current, double power) {
        this.reportedTs = reportedTs;
        this.voltage = voltage;
        this.current = current;
        this.power = power;
    }

    public long getReportedTs() {
        return reportedTs;
    }

    public void setReportedTs(long reportedTs) {
        this.reportedTs = reportedTs;
    }

    public double getVoltage() {
        return voltage;
    }

    public void setVoltage(double voltage) {
        this.voltage = voltage;
    }

    public double getCurrent() {
        return current;
    }

    public void setCurrent(double current) {
        this.current = current;
    }

    public double getPower() {
        return power;
    }

    public void setPower(double power) {
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
