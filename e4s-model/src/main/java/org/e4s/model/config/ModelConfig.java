package org.e4s.model.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ModelConfig {

    public static final String ENV_MODELS_PATH = "E4S_MODELS_PATH";
    public static final String PROP_MODELS_PATH = "e4s.models-path";
    public static final String CLI_MODELS_PATH = "models-path";
    public static final String DEFAULT_MODELS_XML = "models.xml";

    private ModelConfig() {
    }

    public static String resolveModelPath(String cliArg) {
        if (cliArg != null && !cliArg.isBlank()) {
            validateAbsolutePath(cliArg);
            validateFileExists(cliArg);
            return cliArg;
        }

        String envPath = System.getenv(ENV_MODELS_PATH);
        if (envPath != null && !envPath.isBlank()) {
            validateAbsolutePath(envPath);
            validateFileExists(envPath);
            return envPath;
        }

        String propPath = System.getProperty(PROP_MODELS_PATH);
        if (propPath != null && !propPath.isBlank()) {
            validateAbsolutePath(propPath);
            validateFileExists(propPath);
            return propPath;
        }

        return DEFAULT_MODELS_XML;
    }

    public static void validateAbsolutePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(buildNotAbsoluteError(path));
        }
        
        Path p = Paths.get(path);
        if (!p.isAbsolute()) {
            throw new IllegalArgumentException(buildNotAbsoluteError(path));
        }
    }

    public static void validateFileExists(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(buildNotFoundError(path, "not provided"));
        }
        
        if (path.equals(DEFAULT_MODELS_XML)) {
            return;
        }
        
        if (!Files.exists(Paths.get(path))) {
            String source = determineSource(path);
            throw new IllegalArgumentException(buildNotFoundError(path, source));
        }
    }

    public static String computeModelHash(String path) {
        try {
            byte[] content;
            
            if (path.equals(DEFAULT_MODELS_XML)) {
                content = loadFromClasspath(DEFAULT_MODELS_XML);
            } else {
                content = Files.readAllBytes(Paths.get(path));
            }
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to compute model hash for: " + path, e);
        }
    }

    private static byte[] loadFromClasspath(String resource) throws IOException {
        try (var is = ModelConfig.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IOException("Resource not found on classpath: " + resource);
            }
            return is.readAllBytes();
        }
    }

    public static boolean isClasspathResource(String path) {
        return path.equals(DEFAULT_MODELS_XML) || !Paths.get(path).isAbsolute();
    }

    public static String getResolutionInfo(String cliArg) {
        StringBuilder sb = new StringBuilder();
        sb.append("Resolving models.xml path...\n");
        
        String used = null;
        String source = null;
        
        if (cliArg != null && !cliArg.isBlank()) {
            used = cliArg;
            source = "CLI argument";
        } else {
            String envPath = System.getenv(ENV_MODELS_PATH);
            if (envPath != null && !envPath.isBlank()) {
                used = envPath;
                source = "Environment variable (" + ENV_MODELS_PATH + ")";
            } else {
                String propPath = System.getProperty(PROP_MODELS_PATH);
                if (propPath != null && !propPath.isBlank()) {
                    used = propPath;
                    source = "System property (" + PROP_MODELS_PATH + ")";
                } else {
                    used = DEFAULT_MODELS_XML;
                    source = "Default (classpath)";
                }
            }
        }
        
        sb.append("  CLI argument:  ").append(cliArg != null && !cliArg.isBlank() ? cliArg + " ✓" : "(not provided)").append("\n");
        sb.append("  Environment:   ").append(System.getenv(ENV_MODELS_PATH) != null ? System.getenv(ENV_MODELS_PATH) + " ✓" : "(not set)").append("\n");
        sb.append("  System prop:   ").append(System.getProperty(PROP_MODELS_PATH) != null ? System.getProperty(PROP_MODELS_PATH) + " ✓" : "(not set)").append("\n");
        sb.append("\n");
        sb.append("  Using: ").append(used).append("\n");
        sb.append("  Source: ").append(source);
        
        return sb.toString();
    }

    private static String determineSource(String path) {
        if (path != null && path.equals(System.getenv(ENV_MODELS_PATH))) {
            return "Environment variable (" + ENV_MODELS_PATH + ")";
        }
        if (path != null && path.equals(System.getProperty(PROP_MODELS_PATH))) {
            return "System property (" + PROP_MODELS_PATH + ")";
        }
        return "CLI argument (--" + CLI_MODELS_PATH + ")";
    }

    private static String buildNotAbsoluteError(String path) {
        return """
            
            ERROR: models.xml path must be absolute
              Provided: %s
              Required: /full/path/to/models.xml
            
            Example:
              --models-path=/absolute/path/to/models.xml
              E4S_MODELS_PATH=/absolute/path/to/models.xml
              -De4s.models-path=/absolute/path/to/models.xml
            """.formatted(path != null ? path : "(null)");
    }

    private static String buildNotFoundError(String path, String source) {
        return """
            
            ERROR: models.xml not found
              Path: %s
              Resolved from: %s
            
            Please ensure:
              1. The file exists at the specified path
              2. The path is absolute (starts with /)
              3. The application has read permissions
            
            Example:
              --models-path=/absolute/path/to/models.xml
            """.formatted(path != null ? path : "(null)", source);
    }
}
