#!/bin/sh
set -e;

JAR_FILE="$(ls -ld target/log_access_reader-*.jar 2>/dev/null | cut -d' ' -f9)"
if test -z $JAR_FILE
then
    # Builds the project
    mvn clean install
fi

#echo "Arguments are $@"
#java \
#    -Dcom.sun.management.jmxremote.port=9000 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false \
#    -agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n \
#    -Xms100M -Xmx2G \
#    -jar target/log_access_reader-*.jar \
#    $@
java -Xms100M -Xmx2G -jar target/log_access_reader-*.jar $@
