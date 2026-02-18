package org.e4s.model.dynamic;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ModelDefinitionLoader {

    public List<ModelDefinition> load(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Model definition file not found: " + resourcePath);
            }
            return load(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load model definitions from " + resourcePath, e);
        }
    }

    public List<ModelDefinition> load(InputStream inputStream) {
        List<ModelDefinition> definitions = new ArrayList<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            NodeList modelNodes = document.getElementsByTagName("model");
            
            for (int i = 0; i < modelNodes.getLength(); i++) {
                Element modelElement = (Element) modelNodes.item(i);
                ModelDefinition modelDef = parseModel(modelElement);
                definitions.add(modelDef);
            }

            return definitions;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse model definitions", e);
        }
    }

    private ModelDefinition parseModel(Element modelElement) {
        String name = modelElement.getAttribute("name");
        String packageName = modelElement.getAttribute("package");
        String timestampField = modelElement.getAttribute("timestampField");

        ModelDefinition modelDef = new ModelDefinition(name, packageName);
        if (!timestampField.isEmpty()) {
            modelDef.setTimestampField(timestampField);
        }

        NodeList fieldNodes = modelElement.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element fieldElement = (Element) fieldNodes.item(i);
            FieldDefinition fieldDef = parseField(fieldElement);
            modelDef.addField(fieldDef);
        }

        return modelDef;
    }

    private FieldDefinition parseField(Element fieldElement) {
        String name = fieldElement.getAttribute("name");
        String typeStr = fieldElement.getAttribute("type");
        String elementType = fieldElement.getAttribute("elementType");

        FieldType type = FieldType.fromString(typeStr);
        FieldDefinition fieldDef = new FieldDefinition(name, type);
        
        if (type == FieldType.ARRAY && !elementType.isEmpty()) {
            fieldDef.setElementType(elementType);
        }

        return fieldDef;
    }
}
