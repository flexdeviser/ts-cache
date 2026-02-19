# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy pom files first for better caching
COPY pom.xml .
COPY e4s-model/pom.xml e4s-model/
COPY e4s-client/pom.xml e4s-client/

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY e4s-model/src e4s-model/src
COPY e4s-client/src e4s-client/src

# Build the application
RUN mvn clean package -DskipTests -B

# Copy dependencies to separate folder
RUN cd e4s-client && mvn dependency:copy-dependencies -DoutputDirectory=target/dependency

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r e4s && useradd -r -g e4s e4s

# Copy JAR from builder
COPY --from=builder /build/e4s-client/target/e4s-client-*.jar app.jar

# Copy dependencies
COPY --from=builder /build/e4s-client/target/dependency/ dependency/

# Copy models.xml (can be overridden via volume mount)
COPY --from=builder /build/e4s-model/src/main/resources/models.xml /app/config/models.xml

# Create directories
RUN mkdir -p /app/logs && chown -R e4s:e4s /app

USER e4s

# JVM options
ENV JAVA_OPTS="-Xms256m -Xmx1g -XX:+UseG1GC"

# Default models path
ENV E4S_MODELS_PATH="/app/config/models.xml"

# Default server address
ENV E4S_ADDRESS="e4s-server:5701"

# Build classpath
ENV CLASSPATH="/app/app.jar:/app/dependency/*"

# Entry point - requires MAIN_CLASS to be set
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -De4s.models-path=$E4S_MODELS_PATH -cp $CLASSPATH $MAIN_CLASS"]
