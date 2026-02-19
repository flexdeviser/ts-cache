# Docker Deployment

This guide explains how to build and run the E4S server and client using Docker.

## Prerequisites

- Docker 20.10+
- Docker Compose 2.0+

## Quick Start

### Build Images

```bash
# Build all images
docker-compose build

# Or build individually
docker-compose build e4s-server
docker-compose build e4s-client
```

### Run Server

```bash
# Start server only
docker-compose up e4s-server

# Run in background
docker-compose up -d e4s-server

# Check logs
docker-compose logs -f e4s-server
```

### Run Client

```bash
# Start server and client together
docker-compose --profile client up

# Or run client separately (server must be running)
docker-compose --profile client up e4s-client
```

## Configuration

### Server Configuration

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Xms1g -Xmx4g -XX:+UseG1GC` | JVM options |
| `E4S_MODELS_PATH` | `/app/config/models.xml` | Path to models.xml |
| `E4S_RETENTION_DAYS` | `21` | Data retention in days |
| `E4S_IDLE_HOURS` | `24` | Idle hours before eviction |

### Client Configuration

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Xms256m -Xmx1g` | JVM options |
| `E4S_MODELS_PATH` | `/app/config/models.xml` | Path to models.xml |
| `E4S_ADDRESS` | `e4s-server:5701` | Hazelcast server address |
| `MAIN_CLASS` | `org.e4s.client.example.ExampleClient` | Main class to run |

### Custom models.xml

Mount your custom models.xml:

```yaml
services:
  e4s-server:
    volumes:
      - /path/to/your/models.xml:/app/config/models.xml:ro
```

Or use environment variable:

```bash
docker run -e E4S_MODELS_PATH=/custom/path/models.xml ...
```

## Usage Examples

### Run Example Client

```bash
docker-compose --profile client up e4s-client
```

### Run Benchmark

```bash
docker run --rm \
  -e E4S_ADDRESS=your-server:5701 \
  -e MAIN_CLASS=org.e4s.client.hazelcast.ClientBenchmark \
  e4s-client:latest
```

### Run Custom Client

```bash
docker run --rm \
  -e E4S_ADDRESS=your-server:5701 \
  -e MAIN_CLASS=com.yourcompany.YourClient \
  -v /path/to/models.xml:/app/config/models.xml:ro \
  e4s-client:latest
```

### Interactive Shell

```bash
# Server
docker-compose exec e4s-server /bin/bash

# Client
docker run --rm -it --entrypoint /bin/bash e4s-client:latest
```

## Production Deployment

### Resource Limits

```yaml
services:
  e4s-server:
    deploy:
      resources:
        limits:
          cpus: '4'
          memory: 8G
        reservations:
          cpus: '2'
          memory: 4G
```

### Health Checks

Server includes built-in health check:

```bash
# Check health
curl http://localhost:8080/actuator/health

# Docker health status
docker-compose ps
```

### Logging

```yaml
services:
  e4s-server:
    logging:
      driver: "json-file"
      options:
        max-size: "100m"
        max-file: "5"
```

### Network Isolation

```yaml
networks:
  e4s-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

## Troubleshooting

### Check Server Logs

```bash
docker-compose logs -f e4s-server
```

### Check Client Model Validation

```bash
docker-compose logs e4s-client | grep -i "model validation"
```

### Connect to Running Container

```bash
docker-compose exec e4s-server /bin/bash
```

### Inspect Network

```bash
docker network inspect ts-cache_e4s-network
```

## Building from Source

```bash
# Clone repository
git clone https://github.com/yourorg/ts-cache.git
cd ts-cache

# Build images
docker-compose build

# Run
docker-compose up
```

## Multi-Stage Build

Both Dockerfiles use multi-stage builds:

1. **Builder stage**: Compiles the application with Maven
2. **Runtime stage**: Minimal JRE image with only necessary files

This results in smaller images:

- Server: ~400MB
- Client: ~350MB
