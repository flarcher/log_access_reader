FROM maven:3-jdk-8-slim

COPY pom.xml /tmp/
COPY src /tmp/src/
COPY resources /tmp/resources/
WORKDIR /tmp/

CMD mvn install