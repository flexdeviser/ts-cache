package org.e4s.server;

import org.e4s.model.config.ModelConfig;
import org.e4s.model.dynamic.DynamicModelRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class E4sServerApplication {

    public static void main(String[] args) {
        String modelsPath = extractModelsPath(args);
        
        if (modelsPath != null) {
            System.setProperty(ModelConfig.PROP_MODELS_PATH, modelsPath);
        }
        
        DynamicModelRegistry.getInstance().initialize(modelsPath);
        
        SpringApplication.run(E4sServerApplication.class, args);
    }
    
    private static String extractModelsPath(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--" + ModelConfig.CLI_MODELS_PATH)) {
                if (i + 1 < args.length) {
                    return args[i + 1];
                }
            } else if (args[i].startsWith("--" + ModelConfig.CLI_MODELS_PATH + "=")) {
                return args[i].substring(("--" + ModelConfig.CLI_MODELS_PATH + "=").length());
            }
        }
        return null;
    }
}
