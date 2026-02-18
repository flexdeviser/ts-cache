package org.e4s.model.dynamic;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BucketInterceptors {

    public static class TouchInterceptor {
        @RuntimeType
        public static Object intercept(@This Object self) throws Exception {
            Field lastAccessTimeField = self.getClass().getDeclaredField("lastAccessTime");
            lastAccessTimeField.setAccessible(true);
            lastAccessTimeField.setLong(self, System.currentTimeMillis());
            return null;
        }
    }

    public static class AddReadingInterceptor {
        private final ByteBuddyClassGenerator.BucketMethodContext context;

        public AddReadingInterceptor(ByteBuddyClassGenerator.BucketMethodContext context) {
            this.context = context;
        }

        @RuntimeType
        public Object intercept(@This Object self, @RuntimeType Object reading) throws Exception {
            Field readingsField = self.getClass().getDeclaredField(context.arrayFieldName);
            readingsField.setAccessible(true);
            Field countField = context.countFieldName != null 
                    ? self.getClass().getDeclaredField(context.countFieldName) : null;
            if (countField != null) countField.setAccessible(true);
            
            Object[] readings = (Object[]) readingsField.get(self);
            int count = countField != null ? countField.getInt(self) : 0;
            
            if (readings == null) {
                ensureCapacity(self, 1);
                readings = (Object[]) readingsField.get(self);
            }
            
            long newTimestamp = getTimestamp(reading);
            
            for (int i = 0; i < count; i++) {
                Object existing = readings[i];
                long existingTs = getTimestamp(existing);
                if (existingTs == newTimestamp) {
                    readings[i] = reading;
                    touch(self);
                    return null;
                }
            }
            
            ensureCapacity(self, count + 1);
            readings = (Object[]) readingsField.get(self);
            readings[count] = reading;
            if (countField != null) {
                countField.setInt(self, count + 1);
            }
            touch(self);
            return null;
        }

        private long getTimestamp(Object reading) throws Exception {
            String getterName = "get" + Character.toUpperCase(context.timestampField.charAt(0)) +
                               context.timestampField.substring(1);
            Method getter = reading.getClass().getMethod(getterName);
            return (Long) getter.invoke(reading);
        }

        private void ensureCapacity(Object self, int minCapacity) throws Exception {
            Method ensureMethod = self.getClass().getDeclaredMethod("ensureCapacity", int.class);
            ensureMethod.setAccessible(true);
            ensureMethod.invoke(self, minCapacity);
        }

        private void touch(Object self) throws Exception {
            Method touchMethod = self.getClass().getMethod("touch");
            touchMethod.invoke(self);
        }
    }

    public static class AddReadingsInterceptor {
        @RuntimeType
        public Object intercept(@This Object self, @RuntimeType Object[] newReadings) throws Exception {
            if (newReadings == null || newReadings.length == 0) {
                return null;
            }
            
            Method addMethod = self.getClass().getMethod("addReading", 
                    newReadings.getClass().getComponentType());
            for (Object reading : newReadings) {
                addMethod.invoke(self, reading);
            }
            return null;
        }
    }

    public static class EnsureCapacityInterceptor {
        private final ByteBuddyClassGenerator.BucketMethodContext context;

        public EnsureCapacityInterceptor(ByteBuddyClassGenerator.BucketMethodContext context) {
            this.context = context;
        }

        @RuntimeType
        public Object intercept(@This Object self, int minCapacity) throws Exception {
            Field readingsField = self.getClass().getDeclaredField(context.arrayFieldName);
            readingsField.setAccessible(true);
            
            Object[] readings = (Object[]) readingsField.get(self);
            if (readings == null) {
                Object newArray = java.lang.reflect.Array.newInstance(context.readingType, Math.max(minCapacity, 16));
                readingsField.set(self, newArray);
                return null;
            }
            
            if (readings.length < minCapacity) {
                int newCapacity = Math.max(minCapacity, readings.length + (readings.length >> 1));
                Object[] newReadings = (Object[]) java.lang.reflect.Array.newInstance(
                        context.readingType, newCapacity);
                System.arraycopy(readings, 0, newReadings, 0, readings.length);
                readingsField.set(self, newReadings);
            }
            return null;
        }
    }

    public static class TrimToSizeInterceptor {
        private final ByteBuddyClassGenerator.BucketMethodContext context;

        public TrimToSizeInterceptor(ByteBuddyClassGenerator.BucketMethodContext context) {
            this.context = context;
        }

        @RuntimeType
        public Object intercept(@This Object self) throws Exception {
            Field readingsField = self.getClass().getDeclaredField(context.arrayFieldName);
            readingsField.setAccessible(true);
            Field countField = context.countFieldName != null 
                    ? self.getClass().getDeclaredField(context.countFieldName) : null;
            if (countField != null) countField.setAccessible(true);
            
            Object[] readings = (Object[]) readingsField.get(self);
            int count = countField != null ? countField.getInt(self) : 0;
            
            if (readings != null && readings.length > count) {
                if (count == 0) {
                    Object emptyArray = java.lang.reflect.Array.newInstance(context.readingType, 0);
                    readingsField.set(self, emptyArray);
                } else {
                    Object[] trimmed = (Object[]) java.lang.reflect.Array.newInstance(
                            context.readingType, count);
                    System.arraycopy(readings, 0, trimmed, 0, count);
                    readingsField.set(self, trimmed);
                }
            }
            return null;
        }
    }
}
