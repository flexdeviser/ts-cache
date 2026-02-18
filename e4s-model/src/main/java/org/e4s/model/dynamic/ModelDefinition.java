package org.e4s.model.dynamic;

import java.util.ArrayList;
import java.util.List;

public class ModelDefinition {
    private String name;
    private String packageName;
    private String timestampField;
    private List<FieldDefinition> fields = new ArrayList<>();

    public ModelDefinition() {
    }

    public ModelDefinition(String name, String packageName) {
        this.name = name;
        this.packageName = packageName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getTimestampField() {
        return timestampField;
    }

    public void setTimestampField(String timestampField) {
        this.timestampField = timestampField;
    }

    public List<FieldDefinition> getFields() {
        return fields;
    }

    public void setFields(List<FieldDefinition> fields) {
        this.fields = fields;
    }

    public void addField(FieldDefinition field) {
        field.setOrder(fields.size());
        this.fields.add(field);
    }

    public String getFullName() {
        return packageName + "." + name;
    }

    public FieldDefinition getFieldByName(String name) {
        return fields.stream()
                .filter(f -> f.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public FieldDefinition getArrayField() {
        return fields.stream()
                .filter(f -> f.getType() == FieldType.ARRAY)
                .findFirst()
                .orElse(null);
    }

    public FieldDefinition getCountField() {
        return fields.stream()
                .filter(f -> f.getName().endsWith("Count"))
                .findFirst()
                .orElse(null);
    }

    public boolean hasArrayField() {
        return fields.stream().anyMatch(f -> f.getType() == FieldType.ARRAY);
    }

    @Override
    public String toString() {
        return "ModelDefinition{" +
                "name='" + name + '\'' +
                ", packageName='" + packageName + '\'' +
                ", timestampField='" + timestampField + '\'' +
                ", fields=" + fields +
                '}';
    }
}
