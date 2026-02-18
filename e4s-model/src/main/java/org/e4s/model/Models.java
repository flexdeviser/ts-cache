package org.e4s.model;

import org.e4s.model.dynamic.DynamicModelRegistry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;

public final class Models {

    private static final String READING_MODEL = "MeterReading";
    
    private static volatile boolean initialized = false;
    
    private Models() {
    }
    
    public static synchronized void initialize() {
        if (!initialized) {
            DynamicModelRegistry.getInstance().initialize("models.xml");
            initialized = true;
        }
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    public static Class<?> readingClass() {
        ensureInitialized();
        return DynamicModelRegistry.getInstance().getClass(READING_MODEL);
    }
    
    @SuppressWarnings("unchecked")
    public static Class<? extends Timestamped> getReadingClass() {
        ensureInitialized();
        return (Class<? extends Timestamped>) DynamicModelRegistry.getInstance().getClass(READING_MODEL);
    }
    
    public static Timestamped newReading(long reportedTs, double voltage, double current, double power) {
        ensureInitialized();
        try {
            Class<?> readingClass = readingClass();
            Constructor<?> ctor = readingClass.getDeclaredConstructor();
            Object instance = ctor.newInstance();
            
            Method setReportedTs = readingClass.getMethod("setReportedTs", long.class);
            setReportedTs.invoke(instance, reportedTs);
            
            Method setVoltage = readingClass.getMethod("setVoltage", double.class);
            setVoltage.invoke(instance, voltage);
            
            Method setCurrent = readingClass.getMethod("setCurrent", double.class);
            setCurrent.invoke(instance, current);
            
            Method setPower = readingClass.getMethod("setPower", double.class);
            setPower.invoke(instance, power);
            
            return (Timestamped) instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MeterReading", e);
        }
    }
    
    public static GenericBucket<Timestamped> newBucket(String meterId, long bucketDateEpochDay) {
        ensureInitialized();
        Class<? extends Timestamped> readingClass = getReadingClass();
        return new GenericBucket<>(meterId, bucketDateEpochDay, (Class<Timestamped>) readingClass);
    }
    
    public static GenericBucket<Timestamped> newBucket(String meterId, LocalDate date) {
        return newBucket(meterId, date.toEpochDay());
    }
    
    public static long getReportedTs(Object reading) {
        return ((Timestamped) reading).getTimestamp();
    }
    
    public static void setReportedTs(Object reading, long reportedTs) {
        try {
            Method m = reading.getClass().getMethod("setReportedTs", long.class);
            m.invoke(reading, reportedTs);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set reportedTs", e);
        }
    }
    
    public static double getVoltage(Object reading) {
        try {
            Method m = reading.getClass().getMethod("getVoltage");
            return (Double) m.invoke(reading);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get voltage", e);
        }
    }
    
    public static double getCurrent(Object reading) {
        try {
            Method m = reading.getClass().getMethod("getCurrent");
            return (Double) m.invoke(reading);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get current", e);
        }
    }
    
    public static double getPower(Object reading) {
        try {
            Method m = reading.getClass().getMethod("getPower");
            return (Double) m.invoke(reading);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get power", e);
        }
    }
    
    private static void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }
}
