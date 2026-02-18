package org.e4s.model.dynamic;

public class FieldDefinition {
    private String name;
    private FieldType type;
    private String elementType;
    private int order;

    public FieldDefinition() {
    }

    public FieldDefinition(String name, FieldType type) {
        this.name = name;
        this.type = type;
    }

    public FieldDefinition(String name, FieldType type, String elementType) {
        this.name = name;
        this.type = type;
        this.elementType = elementType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public FieldType getType() {
        return type;
    }

    public void setType(FieldType type) {
        this.type = type;
    }

    public String getElementType() {
        return elementType;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getCapitalizedName() {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public String getGetterName() {
        if (type == FieldType.BOOLEAN) {
            return "is" + getCapitalizedName();
        }
        return "get" + getCapitalizedName();
    }

    public String getSetterName() {
        return "set" + getCapitalizedName();
    }

    @Override
    public String toString() {
        return "FieldDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", elementType='" + elementType + '\'' +
                '}';
    }
}
