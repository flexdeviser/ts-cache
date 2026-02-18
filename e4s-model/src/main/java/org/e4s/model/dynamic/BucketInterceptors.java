package org.e4s.model.dynamic;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BucketInterceptors {

    public static class TouchInterceptor {
        public static void intercept(@This Object self) throws Exception {
            Field lastAccessTimeField = self.getClass().getDeclaredField("lastAccessTime");
            lastAccessTimeField.setAccessible(true);
            lastAccessTimeField.setLong(self, System.currentTimeMillis());
        }
    }

    public static class AddReadingInterceptor {
        @RuntimeType
        public static void intercept(@This Object self, @AllArguments Object[] args) throws Exception {
            Object reading = args[0];
            
            Field readingsField = self.getClass().getDeclaredField("readings");
            readingsField.setAccessible(true);
            Field countField = self.getClass().getDeclaredField("readingCount");
            countField.setAccessible(true);
            
            Object[] readings = (Object[]) readingsField.get(self);
            int count = countField.getInt(self);
            
            if (readings == null || readings.length == 0) {
                Class<?> componentType = reading.getClass();
                Object newArray = Array.newInstance(componentType, 16);
                readingsField.set(self, newArray);
                readings = (Object[]) readingsField.get(self);
            }
            
            long newTimestamp = getTimestamp(reading);
            
            for (int i = 0; i < count; i++) {
                Object existing = readings[i];
                long existingTs = getTimestamp(existing);
                if (existingTs == newTimestamp) {
                    readings[i] = reading;
                    touch(self);
                    return;
                }
            }
            
            if (count >= readings.length) {
                int newCapacity = readings.length + (readings.length >> 1);
                Class<?> componentType = readings.getClass().getComponentType();
                Object[] newReadings = (Object[]) Array.newInstance(componentType, newCapacity);
                System.arraycopy(readings, 0, newReadings, 0, count);
                readingsField.set(self, newReadings);
                readings = newReadings;
            }
            
            readings[count] = reading;
            countField.setInt(self, count + 1);
            touch(self);
        }

        private static long getTimestamp(Object reading) throws Exception {
            Method getter = reading.getClass().getMethod("getReportedTs");
            return (Long) getter.invoke(reading);
        }

        private static void touch(Object self) throws Exception {
            Field lastAccessTimeField = self.getClass().getDeclaredField("lastAccessTime");
            lastAccessTimeField.setAccessible(true);
            lastAccessTimeField.setLong(self, System.currentTimeMillis());
        }
    }

    public static class AddReadingsInterceptor {
        public static void intercept(@This Object self, @AllArguments Object[] args) throws Exception {
            Object[] newReadings = (Object[]) args[0];
            if (newReadings == null || newReadings.length == 0) {
                return;
            }
            
            Method addMethod = self.getClass().getMethod("addReading", newReadings.getClass().getComponentType());
            for (Object reading : newReadings) {
                addMethod.invoke(self, reading);
            }
        }
    }

    public static class EnsureCapacityInterceptor {
        public static void intercept(@This Object self, @AllArguments Object[] args) throws Exception {
            int minCapacity = (Integer) args[0];
            
            Field readingsField = self.getClass().getDeclaredField("readings");
            readingsField.setAccessible(true);
            
            Object[] readings = (Object[]) readingsField.get(self);
            
            if (readings == null) {
                Field countField = self.getClass().getDeclaredField("readingCount");
                countField.setAccessible(true);
                int count = countField.getInt(self);
                
                Class<?> readingType = readings.getClass().getComponentType();
                Object newArray = Array.newInstance(readingType, Math.max(minCapacity, 16));
                readingsField.set(self, newArray);
                return;
            }
            
            if (readings.length < minCapacity) {
                int newCapacity = Math.max(minCapacity, readings.length + (readings.length >> 1));
                Class<?> readingType = readings.getClass().getComponentType();
                Object[] newReadings = (Object[]) Array.newInstance(readingType, newCapacity);
                System.arraycopy(readings, 0, newReadings, 0, readings.length);
                readingsField.set(self, newReadings);
            }
        }
    }

    public static class TrimToSizeInterceptor {
        public static void intercept(@This Object self) throws Exception {
            Field readingsField = self.getClass().getDeclaredField("readings");
            readingsField.setAccessible(true);
            Field countField = self.getClass().getDeclaredField("readingCount");
            countField.setAccessible(true);
            
            Object[] readings = (Object[]) readingsField.get(self);
            int count = countField.getInt(self);
            
            if (readings != null && readings.length > count) {
                if (count == 0) {
                    Class<?> readingType = readings.getClass().getComponentType();
                    Object emptyArray = Array.newInstance(readingType, 0);
                    readingsField.set(self, emptyArray);
                } else {
                    Class<?> readingType = readings.getClass().getComponentType();
                    Object[] trimmed = (Object[]) Array.newInstance(readingType, count);
                    System.arraycopy(readings, 0, trimmed, 0, count);
                    readingsField.set(self, trimmed);
                }
            }
        }
    }
}
