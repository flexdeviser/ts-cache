#!/bin/bash

# E4S Server Launcher
# Usage: ./start-server.sh [options]
#
# Options:
#   --models-path <path>  Absolute path to models.xml file
#   --java-opts <opts>    Additional JVM options
#   --port <port>         Server port (default: 8080)
#   --help                Show this help message
#
# Configuration Priority (highest to lowest):
#   1. CLI argument (--models-path)
#   2. Environment variable (E4S_MODELS_PATH)
#   3. System property (-De4s.models-path=...)
#   4. Default (classpath:models.xml)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"

MODELS_PATH=""
JAVA_OPTS=""
SERVER_PORT=""
JAR_FILE=""

usage() {
    echo "E4S Server Launcher"
    echo ""
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  --models-path <path>  Absolute path to models.xml file"
    echo "  --java-opts <opts>    Additional JVM options"
    echo "  --port <port>         Server port (default: 8080)"
    echo "  --jar <path>          Path to server jar file"
    echo "  --help                Show this help message"
    echo ""
    echo "Configuration Priority (highest to lowest):"
    echo "  1. CLI argument (--models-path)"
    echo "  2. Environment variable (E4S_MODELS_PATH)"
    echo "  3. System property (-De4s.models-path=...)"
    echo "  4. Default (classpath:models.xml)"
    echo ""
    echo "Examples:"
    echo "  $0 --models-path /data/config/models.xml"
    echo "  E4S_MODELS_PATH=/data/config/models.xml $0"
    echo "  $0 --java-opts \"-Xmx4g -Xms2g\" --port 9090"
    exit 0
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --models-path)
            MODELS_PATH="$2"
            shift 2
            ;;
        --java-opts)
            JAVA_OPTS="$2"
            shift 2
            ;;
        --port)
            SERVER_PORT="$2"
            shift 2
            ;;
        --jar)
            JAR_FILE="$2"
            shift 2
            ;;
        --help|-h)
            usage
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Validate models path if provided
if [ -n "$MODELS_PATH" ]; then
    if [ ! -f "$MODELS_PATH" ]; then
        echo "ERROR: models.xml not found at: $MODELS_PATH"
        exit 1
    fi
    
    if [[ "$MODELS_PATH" != /* ]]; then
        echo "ERROR: models-path must be absolute: $MODELS_PATH"
        echo "Example: --models-path=/absolute/path/to/models.xml"
        exit 1
    fi
    
    echo "Using models.xml from: $MODELS_PATH"
fi

# Find jar file if not specified
if [ -z "$JAR_FILE" ]; then
    # Check in target directory
    JAR_FILE=$(find "$APP_HOME/target" -name "e4s-server*.jar" -type f ! -name "*sources*" ! -name "*original*" | head -1)
    
    if [ -z "$JAR_FILE" ]; then
        echo "ERROR: Could not find e4s-server.jar"
        echo "Please specify with --jar option or build the project first: mvn clean package -DskipTests"
        exit 1
    fi
fi

echo "=============================================="
echo "Starting E4S Server"
echo "=============================================="
echo "  JAR: $JAR_FILE"
echo "  Models: ${MODELS_PATH:-classpath:models.xml}"
if [ -n "$SERVER_PORT" ]; then
    echo "  Port: $SERVER_PORT"
fi
echo "=============================================="
echo ""

# Build Java options
if [ -n "$SERVER_PORT" ]; then
    JAVA_OPTS="$JAVA_OPTS -Dserver.port=$SERVER_PORT"
fi

# Start server
exec java $JAVA_OPTS -jar "$JAR_FILE" \
    ${MODELS_PATH:+--models-path="$MODELS_PATH"}
