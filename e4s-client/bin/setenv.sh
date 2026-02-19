#!/bin/bash

# E4S Client Environment Configuration
# Source this file to set environment variables
#
# Usage: source bin/setenv.sh

# Models.xml path (optional, absolute path)
# Priority: E4S_MODELS_PATH > System Property > Default classpath
export E4S_MODELS_PATH="${E4S_MODELS_PATH:-}"

# Hazelcast server address
export E4S_ADDRESS="${E4S_ADDRESS:-localhost:5701}"

# JVM options
export E4S_JAVA_OPTS="${E4S_JAVA_OPTS:--Xms256m -Xmx1g}"

echo "E4S Client Environment:"
echo "  E4S_MODELS_PATH: ${E4S_MODELS_PATH:-<not set>}"
echo "  E4S_ADDRESS:     $E4S_ADDRESS"
echo "  E4S_JAVA_OPTS:   $E4S_JAVA_OPTS"
