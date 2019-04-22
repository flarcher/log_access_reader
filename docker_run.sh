#!/bin/bash
# Runs the project using Docker
# See https://hub.docker.com/_/openjdk/
#
# The first argument is the location of the access log file

set -e;

ACCESS_LOG_PATH="${1:-/tmp/access.log}"
ACCESS_LOG_DIR="$(dirname $ACCESS_LOG_PATH)"
ACCESS_LOG_FILE="${ACCESS_LOG_PATH#"${ACCESS_LOG_DIR}/"}"

JMX_PORT=9000
DEBUG_PORT=8000
MAX_HEAP_SIZE=2G

JAR_FILE="$(ls -ld target/log-n-cat-*.jar 2>/dev/null | cut -d' ' -f9)"
if test -z $JAR_FILE
then
    # Builds the project
    sh docker_build.sh
fi

# Runs the Java application with:
# - a JMX port 9000 on the host
# - a debug port 8000 on the host
# - container limited resources enabled
# - some heap size configuration
docker run --rm -it --name lnc-run \
    -v "$(pwd)":/usr/src/myapp \
    -w /usr/src/myapp \
    -v "${ACCESS_LOG_DIR}":/tmp \
    -p "127.0.0.1:${JMX_PORT}:9000" \
    -p "127.0.0.1:${DEBUG_PORT}:8000" \
    openjdk:8-jre-slim-stretch \
    java \
    -Dcom.sun.management.jmxremote.port=9000 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false \
    -agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n \
    -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
    -Xms100M "-Xmx${MAX_HEAP_SIZE}" \
    -jar "/usr/src/myapp/${JAR_FILE}" \
    -f "/tmp/${ACCESS_LOG_FILE}"
