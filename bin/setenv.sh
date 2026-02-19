#!/bin/bash

# E4S Environment Configuration
# Source this file to set environment variables
#
# Usage: source bin/setenv.sh

# Models.xml path (optional, absolute path)
# Priority: E4S_MODELS_PATH > System Property > Default classpath
export E4S_MODELS_PATH="${E4S_MODELS_PATH:-}"

# Hazelcast server address
export E4S_ADDRESS="${E4S_ADDRESS:-localhost:5701}"

# Server HTTP URL for validation
export E4S_HTTP_URL="${E4S_HTTP_URL:-http://localhost:8080}"

# JVM options
export E4S_JAVA_OPTS="${E4S_JAVA_OPTS:--Xms512m -Xmx2g}"

# Server port
export E4S_SERVER_PORT="${E4S_SERVER_PORT:-8080}"

# Retention days
export E4S_RETENTION_DAYS="${E4S_RETENTION_DAYS:-21}"

# Idle hours before eviction
export E4S_IDLE_HOURS="${E4S_IDLE_HOURS:-24}"

echo "E4S Environment:"
echo "  E4S_MODELS_PATH:   ${E4S_MODELS_PATH:-<not set>}"
echo "  E4S_ADDRESS:       $E4S_ADDRESS"
echo "  E4S_HTTP_URL:      $E4S_HTTP_URL"
echo "  E4S_SERVER_PORT:   $E4S_SERVER_PORT"
echo "  E4S_JAVA_OPTS:     $E4S_JAVA_OPTS"
