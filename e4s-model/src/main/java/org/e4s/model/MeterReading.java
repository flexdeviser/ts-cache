package org.e4s.model;

/**
 * Represents a single meter reading with timestamp and electrical measurements.
 * 
 * <p>This class is optimized for memory efficiency using primitive types instead of
 * object wrappers. Each reading occupies approximately 32 bytes in memory:
 * <ul>
 *   <li>reportedTs (long): 8 bytes - epoch milliseconds</li>
 *   <li>voltage (double): 8 bytes - in Volts</li>
 *   <li>current (double): 8 bytes - in Amperes</li>
 *   <li>power (double): 8 bytes - in Watts</li>
 * </ul>
 * 
 * <p>Design decisions:
 * <ul>
 *   <li>Uses {@code long} for timestamp instead of {@code Instant} to avoid object overhead</li>
 *   <li>Uses {@code double} instead of {@code BigDecimal} for acceptable precision with lower memory</li>
 *   <li>No additional fields beyond essentials to minimize memory footprint</li>
 * </ul>
 * 
 * @see MeterBucket
 * @see MeterDayKey
 */
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
