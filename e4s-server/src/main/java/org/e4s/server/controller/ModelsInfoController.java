package org.e4s.server.controller;

import org.e4s.model.dynamic.DynamicModelRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/models")
public class ModelsInfoController {

    @GetMapping("/info")
    public ResponseEntity<ModelsInfo> getModelsInfo() {
        DynamicModelRegistry registry = DynamicModelRegistry.getInstance();
        
        ModelsInfo info = new ModelsInfo();
        info.setModelsPath(registry.getModelsPath());
        info.setHash(registry.getModelsHash());
        info.setModelCount(registry.getModelCount());
        info.setModels(registry.getModelNames());
        
        return ResponseEntity.ok(info);
    }

    @GetMapping("/hash")
    public ResponseEntity<Map<String, String>> getModelsHash() {
        DynamicModelRegistry registry = DynamicModelRegistry.getInstance();
        
        Map<String, String> response = new HashMap<>();
        response.put("hash", registry.getModelsHash());
        
        return ResponseEntity.ok(response);
    }

    public static class ModelsInfo {
        private String modelsPath;
        private String hash;
        private int modelCount;
        private Set<String> models;

        public String getModelsPath() {
            return modelsPath;
        }

        public void setModelsPath(String modelsPath) {
            this.modelsPath = modelsPath;
        }

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public int getModelCount() {
            return modelCount;
        }

        public void setModelCount(int modelCount) {
            this.modelCount = modelCount;
        }

        public Set<String> getModels() {
            return models;
        }

        public void setModels(Set<String> models) {
            this.models = models;
        }
    }
}
