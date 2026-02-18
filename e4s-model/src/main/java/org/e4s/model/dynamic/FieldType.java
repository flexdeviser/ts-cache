package org.e4s.model.dynamic;

public enum FieldType {
    STRING(String.class, "writeString", "readString"),
    LONG(long.class, "writeLong", "readLong"),
    INT(int.class, "writeInt", "readInt"),
    DOUBLE(double.class, "writeDouble", "readDouble"),
    FLOAT(float.class, "writeFloat", "readFloat"),
    BOOLEAN(boolean.class, "writeBoolean", "readBoolean"),
    ARRAY(Object[].class, null, null);

    private final Class<?> javaType;
    private final String kryoWriteMethod;
    private final String kryoReadMethod;

    FieldType(Class<?> javaType, String kryoWriteMethod, String kryoReadMethod) {
        this.javaType = javaType;
        this.kryoWriteMethod = kryoWriteMethod;
        this.kryoReadMethod = kryoReadMethod;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    public String getKryoWriteMethod() {
        return kryoWriteMethod;
    }

    public String getKryoReadMethod() {
        return kryoReadMethod;
    }

    public boolean isPrimitive() {
        return this != STRING && this != ARRAY;
    }

    public static FieldType fromString(String type) {
        return switch (type.toLowerCase()) {
            case "string" -> STRING;
            case "long" -> LONG;
            case "int", "integer" -> INT;
            case "double" -> DOUBLE;
            case "float" -> FLOAT;
            case "boolean" -> BOOLEAN;
            case "array" -> ARRAY;
            default -> throw new IllegalArgumentException("Unknown field type: " + type);
        };
    }
}
