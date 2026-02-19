# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy all pom files first for better caching
COPY pom.xml .
RUN mkdir -p e4s-model e4s-server e4s-client
COPY e4s-model/pom.xml e4s-model/
COPY e4s-server/pom.xml e4s-server/
COPY e4s-client/pom.xml e4s-client/

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code - only for modules we're building
COPY e4s-model/src e4s-model/src
COPY e4s-server/src e4s-server/src

# Build the application - only e4s-model and e4s-server
RUN mvn clean package -DskipTests -B -pl e4s-model,e4s-server -am

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r e4s && useradd -r -g e4s e4s

# Copy JAR from builder
COPY --from=builder /build/e4s-server/target/e4s-server-*.jar app.jar

# Copy models.xml (can be overridden via volume mount)
COPY --from=builder /build/e4s-model/src/main/resources/models.xml /app/config/models.xml

# Create directories
RUN mkdir -p /app/logs && chown -R e4s:e4s /app

USER e4s

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM options
ENV JAVA_OPTS="-Xms1g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Default models path
ENV E4S_MODELS_PATH="/app/config/models.xml"

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --models-path=$E4S_MODELS_PATH"]
