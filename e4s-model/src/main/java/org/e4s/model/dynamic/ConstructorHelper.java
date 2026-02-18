package org.e4s.model.dynamic;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.reflect.Field;

public class ConstructorHelper {

    @RuntimeType
    public static void initFields(@This Object self, @AllArguments Object[] args) throws Exception {
        Class<?> clazz = self.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        for (int i = 0; i < fields.length && i < args.length; i++) {
            Field field = fields[i];
            field.setAccessible(true);
            field.set(self, args[i]);
        }
    }
}
