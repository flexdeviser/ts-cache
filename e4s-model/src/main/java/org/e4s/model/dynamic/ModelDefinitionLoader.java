package org.e4s.model.dynamic;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ModelDefinitionLoader {

    public List<ModelDefinition> load(String resourcePath) {
        Path filePath = Paths.get(resourcePath);
        
        if (filePath.isAbsolute() && Files.exists(filePath)) {
            return loadFromPath(filePath);
        }
        
        return loadFromClasspath(resourcePath);
    }

    public List<ModelDefinition> loadFromPath(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            return load(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load model definitions from file: " + path, e);
        }
    }

    public List<ModelDefinition> loadFromClasspath(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Model definition file not found on classpath: " + resourcePath);
            }
            return load(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load model definitions from classpath: " + resourcePath, e);
        }
    }

    public List<ModelDefinition> load(InputStream inputStream) {
        List<ModelDefinition> definitions = new ArrayList<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            
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
        String implementsInterface = modelElement.getAttribute("implements");
        String timestampField = modelElement.getAttribute("timestampField");

        ModelDefinition modelDef = new ModelDefinition(name, packageName);
        if (!implementsInterface.isEmpty()) {
            modelDef.setImplementsInterface(implementsInterface);
        }
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
