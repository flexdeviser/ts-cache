#!/bin/bash

# E4S Native Client Launcher
# Usage: ./start-client.sh [options]
#
# Options:
#   --address <host:port>  Hazelcast server address (default: localhost:5701)
#   --models-path <path>   Absolute path to models.xml file
#   --java-opts <opts>     Additional JVM options
#   --validate             Validate models hash with server HTTP endpoint
#   --http-url <url>       Server HTTP URL for validation (e.g., http://localhost:8080)
#   --help                 Show this help message
#
# Configuration Priority (highest to lowest):
#   1. CLI argument (--models-path)
#   2. Environment variable (E4S_MODELS_PATH)
#   3. System property (-De4s.models-path=...)
#   4. Default (classpath:models.xml)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"

ADDRESS="${E4S_ADDRESS:-localhost:5701}"
MODELS_PATH=""
JAVA_OPTS=""
VALIDATE=false
HTTP_URL=""
MAIN_CLASS=""

usage() {
    echo "E4S Native Client Launcher"
    echo ""
    echo "Usage: $0 [options] [-- <main-class> [args...]]"
    echo ""
    echo "Options:"
    echo "  --address <host:port>  Hazelcast server address (default: localhost:5701)"
    echo "  --models-path <path>   Absolute path to models.xml file"
    echo "  --java-opts <opts>     Additional JVM options"
    echo "  --validate             Validate models hash with server HTTP endpoint"
    echo "  --http-url <url>       Server HTTP URL for validation (e.g., http://localhost:8080)"
    echo "  --main <class>         Main class to run"
    echo "  --help                 Show this help message"
    echo ""
    echo "Configuration Priority (highest to lowest):"
    echo "  1. CLI argument (--models-path)"
    echo "  2. Environment variable (E4S_MODELS_PATH)"
    echo "  3. System property (-De4s.models-path=...)"
    echo "  4. Default (classpath:models.xml)"
    echo ""
    echo "Examples:"
    echo "  $0 --models-path /data/config/models.xml --address server:5701"
    echo "  E4S_MODELS_PATH=/data/config/models.xml $0"
    echo "  $0 --validate --http-url http://localhost:8080 --main com.example.MyClient"
    exit 0
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --address)
            ADDRESS="$2"
            shift 2
            ;;
        --models-path)
            MODELS_PATH="$2"
            shift 2
            ;;
        --java-opts)
            JAVA_OPTS="$2"
            shift 2
            ;;
        --validate)
            VALIDATE=true
            shift
            ;;
        --http-url)
            HTTP_URL="$2"
            shift 2
            ;;
        --main)
            MAIN_CLASS="$2"
            shift 2
            ;;
        --help|-h)
            usage
            ;;
        --)
            shift
            break
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

# Check if models path is set via environment
if [ -z "$MODELS_PATH" ] && [ -n "$E4S_MODELS_PATH" ]; then
    echo "Using models.xml from environment: $E4S_MODELS_PATH"
fi

# Build classpath
CLASSPATH=""
for jar in "$APP_HOME"/e4s-model/target/*.jar "$APP_HOME"/e4s-hzclient/target/*.jar; do
    if [ -f "$jar" ] && [[ ! "$jar" =~ sources ]] && [[ ! "$jar" =~ original ]]; then
        if [ -n "$CLASSPATH" ]; then
            CLASSPATH="$CLASSPATH:"
        fi
        CLASSPATH="$CLASSPATH$jar"
    fi
done

# Add dependencies
for dep in "$APP_HOME"/e4s-model/target/dependency/*.jar "$APP_HOME"/e4s-hzclient/target/dependency/*.jar; do
    if [ -f "$dep" ]; then
        CLASSPATH="$CLASSPATH:$dep"
    fi
done 2>/dev/null

if [ -z "$CLASSPATH" ]; then
    echo "ERROR: Could not build classpath"
    echo "Please build the project first: mvn clean package -DskipTests"
    exit 1
fi

echo "=============================================="
echo "Starting E4S Native Client"
echo "=============================================="
echo "  Address: $ADDRESS"
echo "  Models: ${MODELS_PATH:-${E4S_MODELS_PATH:-classpath:models.xml}}"
echo "  Validate: $VALIDATE"
if [ -n "$MAIN_CLASS" ]; then
    echo "  Main Class: $MAIN_CLASS"
fi
echo "=============================================="
echo ""

# Set system properties
if [ -n "$MODELS_PATH" ]; then
    JAVA_OPTS="$JAVA_OPTS -De4s.models-path=$MODELS_PATH"
fi

if [ "$VALIDATE" = true ] && [ -n "$HTTP_URL" ]; then
    JAVA_OPTS="$JAVA_OPTS -De4s.validate-models=true -De4s.http-url=$HTTP_URL"
fi

# Check if main class is specified
if [ -z "$MAIN_CLASS" ]; then
    echo "ERROR: No main class specified"
    echo "Use --main <class> to specify the main class to run"
    exit 1
fi

# Run client
exec java $JAVA_OPTS -cp "$CLASSPATH" "$MAIN_CLASS" "$@"
