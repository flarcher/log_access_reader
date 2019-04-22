# The JRE is the only requirement
FROM openjdk:8-jre-slim-stretch

# Copy the program
COPY /target/log-n-cat-*.jar /usr/src/myapp/lnc.jar
WORKDIR /usr/src/myapp

# Debugging
EXPOSE 8000
# JMX
EXPOSE 9000
# Default directory for the access.log file
VOLUME /tmp

# Memory configuration
ARG MAX_HEAP_SIZE=2G
ENV JAVA_TOOL_OPTIONS "-Xms100M -Xmx${MAX_HEAP_SIZE:-2G}"

ENTRYPOINT [ "java", "-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n", "-Dcom.sun.management.jmxremote.port=9000", "-Dcom.sun.management.jmxremote.authenticate=false", "-Dcom.sun.management.jmxremote.ssl=false", "-Dfile.encoding=\"UTF-8\"", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "/usr/src/myapp/lnc.jar" ]
CMD [ "-f", "/tmp/access.log" ]