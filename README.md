# HTTP access log reader

Prints statistics and notifies alerts related to HTTP access log files.
This is a Java-based implementation.

## Input specification

According to the [W3C logging control httpd specification](https://www.w3.org/Daemon/User/Config/Logging.html#common-logfile-format), a log line has a format like:

    remotehost rfc931 authuser [date] "request" status bytes

Example log lines:

    127.0.0.1 - james [09/May/2018:16:00:39 +0000] "GET /report HTTP/1.0" 200 123
    127.0.0.1 - jill [09/May/2018:16:00:41 +0000] "GET /api/user HTTP/1.0" 200 234
    127.0.0.1 - frank [09/May/2018:16:00:42 +0000] "POST /api/user HTTP/1.0" 200 34
    127.0.0.1 - mary [09/May/2018:16:00:42 +0000] "POST /api/user HTTP/1.0" 503 12

## How to build

* With Docker, run:

TODO

* Otherwise, you can run the next commands. Prerequisites are:
    * Java Development Kit 8+
    * Maven 3+

```bash
mvn install
```

## Run and usage

After a build,

* If you have Docker installed, you can run:

TODO

* Otherwise, if you have a Java Runtime Environment version 8+ installed, you can run the following command. The last `-h` aims at printing some help about the program usage. 

```bash
java -jar target/log-n-cat-*.jar -h
```

## Configuration

The arguments can be provided in the following ways, ordered by priority:

1. As a command line argument using a flag. E.g: `-f /tmp/access.log` (for configuration of the access log file path). 
2. If no command line argument is given for a configuration entry, a corresponding environment variable will be read. These environment variables have all a prefix `LNC_`. They use uppercase characters and can have underscores.
3. If neither a command line argument nor a corresponding environment variable is provided, then the configuration file would be read, if some has been defined. The path of the configuration file can be provided either with the `-c` command line flag or with the environment variable `LNC_CONFIG_FILE`. The property name matches the environment variable name, without the prefix, with lower case characters and with dots instead of underscores. For example the environment variable `LNC_LOG_FILE` and the property `log.file` are related to the same configuration entry.
4. If no value is given for a configuration entry, a default value will be used.

Use `-h` as an argument in order to get details about all other available arguments. It also outputs all command line
 flags, used environment variables and property names.

## Requirements

* Consume an actively written-to w3c-formatted HTTP access log. It should default to reading /tmp/access.log and be overrideable.  ✓
* Display stats every 10s about the traffic during those 10s: the sections of the web site with the most hits, as well as interesting summary statistics on the traffic as a whole. A section is defined as being what's before the second '/' in the resource section of the log line. For example, the section for "/pages/create" is "/pages" ✓
* Make sure a user can keep the app running and monitor the log file continuously ✓
* Whenever total traffic for the past 2 minutes exceeds a certain number on average, add a message saying that “High traffic generated an alert - hits = {value}, triggered at {time}”. The default threshold should be 10 requests per second, and should be overridable.
* Whenever the total traffic drops again below that value on average for the past 2 minutes, add another message detailing when the alert recovered.
* Make sure all messages showing when alerting thresholds are crossed remain visible on the page for historical reasons.
* Write a test for the alerting logic.
* Explain how you’d improve on this application design.
* If you have access to a linux docker environment, we'd love to be able to docker build and run your project!

## Additional features

* Provide an help message with `-h` or `--help`
* Reading of a configuration file (although in a legacy _properties_ format) whose located can be given as an argument.
* Configuration of the date-time pattern through a parameter `DATE_TIME_FORMAT`. For now, the *LogFileDateExt* access log configuration value can not be used, and the configuration supports only patterns defined according the [Java conventions](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#patterns).
But the value can still be changed depending on *LogFileDateExt* value.
* Use a _Duration_ syntax for configuration argument instead of milliseconds counts. Duration information in configuration entries can have values like `23m` (for 23 minutes) or `1m30s` (for one minute and 30 seconds).
* Display stats at a configurable rate for the latest metrics of a greater period of time. By default, we print  each second the statistics about the last 10 seconds.
*    

## Technical remarks

* There is a robustness/support related to timezone changes (because the timezone offset is read while parsing)
* There is a simple thread-model (reader, main, display)
* It uses aggregation of information in order to reduce memory usage and memory allocation. The gathered information related to a single access log (class `AccessLogLine`) has only a short-term live in the application. The `AccessLogLine` instances are not stored in any collection but are aggregated as soon as possible in classes implementing `Consumer<AccessLogLine>` being `StaticticAggregator` and `TimeBuckets`. These classes aggregate access log information and each instance is related to a whole range of time.
* With a given configuration, the memory consumption is intended to be stable over time. The 2-step aggregations (the first step running over the main idle time, then a second iterating for displayed statistics and alerts over 10 seconds by default) make sure to remove the oldest information (typically being more than 10s old with default configuration)
* A limit about memory use can be configured. A possibly size-growing collection is related to the count of sections in statistics. A huge number of different sections in access log files can increase the memory consumption significantly. In order to address this, the arguments `MAX_SECTION_COUNT_RATIO` and `TOP_SECTION_COUNT` define a maximum count of sections held in memory, so that we can cap the memory consumption according to the gathering of _top sections statistics_.  The lower is the `MAX_SECTION_COUNT_RATIO` value, the bigger is the risk to miss some information related to the _top sections parts_ (however global statistics will stay true in all cases). About (only) the _top sections_ dislpay , the trade-off between precision/correctness and memory consumption explains the reason why the `MAX_SECTION_COUNT_RATIO` exists.
* The metrics aggregation mechanism is shared by the statistics display mechanism and the alerting

## Next steps

* Use a _curses_ like library for console output
* Use a logging library like _logBack_ with an output towards a log file whose path can be configured.
* Provide JMX metrics. Especially about the maximum statictic's section count and the size of the time bucket's internal map.
* Provide metrics about the bytes count.
* Provide stats in several periods of time like; _fromStart_, 1day, 1hour, 5min, 10sec
* Support a better configuration file format instead of the legacy _properties format_, like YAML for example
* Use alternative comparison for top sections (configuration based)
* Support multiple alerts. One of them may have different periods and thresholds. It requires probably a more complex syntax for the configuration file.
* Enhanced alert configuration based on metrics other than only the request throughput. It requires probably a more complex syntax for the configuration file.
* Support of date parsing from most of [`strftime()` patterns](https://www.systutorials.com/docs/linux/man/3-strftime/) according to the *LogFileDateExt* access log configuration.
The value of *LogFileDateExt* could be passed as a parameter and is meaningful since this is the parameter used for the access log formatting known from the user. It should be possible [in Java with some limitations](https://tomcat.apache.org/tomcat-4.1-doc/catalina/docs/api/org/apache/catalina/util/Strftime.html) to use a date-time formatting using the `strftime()` syntax. The access log configuration *LogTime* value is not important since we compute relative durations and absolute instants are not considered. 
* It should be noted, that if the format includes only the time, without the date for example, then the program would not be able to consider times before midnight from the next day. Likewise, the timezone offset should be provided. If any time scope is missing, it would lead to wrong results. So we should make sure that the time is fully defined. It could be done when checking for the input date-time pattern.
* Bring more robustness about inputs not coming in the chronological order? 
* Makes possible to save/restore statistics so that a next start can retrieve the stats of a previous run.
* Have some persistence for gathered metrics 
* Rewrite this application in the *Rust language* in order to get better performance
* Build a lightweight HTTP API so that client programs can easily access the metrics. The HTTP API will need to support some real-time feature (like with HTTP-Streaming or Web-Socket) in order to notify alerts.
* Create some Web-based interface that consumes the HTTP API for a better user experience

## Changelogs

Versions are identified by both Git tags and the POM project version. The version information is also available from 
the JAR file manifest.

|_Version_|_Changes_|
|---|---|
|0.0|Initial version|
|1.0|Statistics sent to the standard output stream|
|2.0|Handling of alerts sent to the standard error stream|
|3.0|Use of curses in a terminal|

## For developers

In order to increase the version using *Git* and *Maven*:

```bash
export LNC_VERSION=X.Y
mvn versions:set "-DnewVersion=$LNC_VERSION"
git add pom.xml
git commit -m "Release of $LNC_VERSION"
git push
git tag -a "$LNC_VERSION" -m 'Put here the version comment'
git push origin "$LNC_VERSION"
mvn versions:set "-DnewVersion=${LNC_VERSION}-SNAPSHOT"
```

## License

Copyright 2019 - **Fabrice LARCHER** _(All rights reserved)_
