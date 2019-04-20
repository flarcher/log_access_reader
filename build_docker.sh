#!/bin/sh
#docker build https://github.com/carlossg/docker-maven.git#master:jdk-8-slim

docker build -t lnc-build .
mkdir -p target
docker run --rm -v `pwd`:/tmp lnc-build
