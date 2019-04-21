#!/bin/sh
# Builds the project using Docker
# See https://hub.docker.com/_/maven
mkdir -p "$HOME/.m2" # In case it does not already exists (for reuse of downloaded artifacts)
docker run --rm --name lnc-build \
    -v "$(pwd)":/usr/src/mymaven \
    -w /usr/src/mymaven \
    -v "$HOME/.m2":/root/.m2 \
    maven:3-jdk-8-slim \
    mvn clean install
