package org.e4s.model;

import org.e4s.model.dynamic.DynamicModelRegistry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Models {

    private static final String READING_MODEL = "MeterReading";
    private static final String BUCKET_MODEL = "MeterBucket";
    
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
    
    public static Class<?> bucketClass() {
        ensureInitialized();
        return DynamicModelRegistry.getInstance().getClass(BUCKET_MODEL);
    }
    
    public static Object newReading(long reportedTs, double voltage, double current, double power) {
        ensureInitialized();
        try {
            return DynamicModelRegistry.getInstance().createInstance(
                    READING_MODEL, reportedTs, voltage, current, power);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MeterReading", e);
        }
    }
    
    public static Object newBucket() {
        ensureInitialized();
        try {
            return DynamicModelRegistry.getInstance().createInstance(BUCKET_MODEL);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MeterBucket", e);
        }
    }
    
    public static Object newBucket(String meterId, long bucketDateEpochDay) {
        ensureInitialized();
        try {
            Class<?> bucketClass = bucketClass();
            Constructor<?> ctor = bucketClass.getConstructor();
            Object bucket = ctor.newInstance();
            
            setMeterId(bucket, meterId);
            setBucketDateEpochDay(bucket, bucketDateEpochDay);
            
            return bucket;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MeterBucket", e);
        }
    }
    
    public static long getReportedTs(Object reading) {
        try {
            Method m = reading.getClass().getMethod("getReportedTs");
            return (Long) m.invoke(reading);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get reportedTs", e);
        }
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
    
    public static String getMeterId(Object bucket) {
        try {
            Method m = bucket.getClass().getMethod("getMeterId");
            return (String) m.invoke(bucket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get meterId", e);
        }
    }
    
    public static void setMeterId(Object bucket, String meterId) {
        try {
            Method m = bucket.getClass().getMethod("setMeterId", String.class);
            m.invoke(bucket, meterId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set meterId", e);
        }
    }
    
    public static long getBucketDateEpochDay(Object bucket) {
        try {
            Method m = bucket.getClass().getMethod("getBucketDateEpochDay");
            return (Long) m.invoke(bucket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get bucketDateEpochDay", e);
        }
    }
    
    public static void setBucketDateEpochDay(Object bucket, long epochDay) {
        try {
            Method m = bucket.getClass().getMethod("setBucketDateEpochDay", long.class);
            m.invoke(bucket, epochDay);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set bucketDateEpochDay", e);
        }
    }
    
    public static Object[] getReadings(Object bucket) {
        try {
            Method m = bucket.getClass().getMethod("getReadings");
            return (Object[]) m.invoke(bucket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get readings", e);
        }
    }
    
    public static void setReadings(Object bucket, Object[] readings) {
        try {
            Method m = bucket.getClass().getMethod("setReadings", readings.getClass());
            m.invoke(bucket, (Object) readings);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set readings", e);
        }
    }
    
    public static int getReadingCount(Object bucket) {
        try {
            Method m = bucket.getClass().getMethod("getReadingCount");
            return (Integer) m.invoke(bucket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get readingCount", e);
        }
    }
    
    public static void addReading(Object bucket, Object reading) {
        try {
            Method m = bucket.getClass().getMethod("addReading", readingClass());
            m.invoke(bucket, reading);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add reading", e);
        }
    }
    
    public static void addReadings(Object bucket, Object[] readings) {
        try {
            Method m = bucket.getClass().getMethod("addReadings", readings.getClass());
            m.invoke(bucket, (Object) readings);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add readings", e);
        }
    }
    
    public static void touch(Object bucket) {
        try {
            Method m = bucket.getClass().getMethod("touch");
            m.invoke(bucket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to touch bucket", e);
        }
    }
    
    public static void trimToSize(Object bucket) {
        try {
            Method m = bucket.getClass().getMethod("trimToSize");
            m.invoke(bucket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to trim bucket", e);
        }
    }
    
    public static long getLastAccessTime(Object bucket) {
        try {
            Method m = bucket.getClass().getMethod("getLastAccessTime");
            return (Long) m.invoke(bucket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get lastAccessTime", e);
        }
    }
    
    public static long getCreatedTime(Object bucket) {
        try {
            Method m = bucket.getClass().getMethod("getCreatedTime");
            return (Long) m.invoke(bucket);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get createdTime", e);
        }
    }
    
    public static List<Object> queryRange(Object bucket, long startTs, long endTs) {
        List<Object> result = new ArrayList<>();
        Object[] readings = getReadings(bucket);
        int count = getReadingCount(bucket);
        
        for (int i = 0; i < count; i++) {
            Object r = readings[i];
            long ts = getReportedTs(r);
            if (ts >= startTs && ts <= endTs) {
                result.add(r);
            }
        }
        
        result.sort(Comparator.comparingLong(Models::getReportedTs));
        return result;
    }
    
    private static void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }
}
