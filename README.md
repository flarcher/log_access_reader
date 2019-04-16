# HTTP access log reader exercise

Prints statistics and notifies alerts by reading access log files.
This is a Java-based implementation.

## Program Requirements

* Consume an actively written-to w3c-formatted HTTP access log. It should default to reading /tmp/access.log and be overrideable.
* Display stats every 10s about the traffic during those 10s: the sections of the web site with the most hits, as well as interesting summary statistics on the traffic as a whole. A section is defined as being what's before the second '/' in the resource section of the log line. For example, the section for "/pages/create" is "/pages"
* Make sure a user can keep the app running and monitor the log file continuously
* Whenever total traffic for the past 2 minutes exceeds a certain number on average, add a message saying that “High traffic generated an alert - hits = {value}, triggered at {time}”. The default threshold should be 10 requests per second, and should be overridable.
* Whenever the total traffic drops again below that value on average for the past 2 minutes, add another message detailing when the alert recovered.
* Make sure all messages showing when alerting thresholds are crossed remain visible on the page for historical reasons.
* Write a test for the alerting logic.
* Explain how you’d improve on this application design.
* If you have access to a linux docker environment, we'd love to be able to docker build and run your project!

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

* Otherwise, if you have a Java Runtime Environment version 8+ installed, you can run the following command. The 
last `-h` aims at printing some help about the program usage. 

```bash
java -jar target/log-n-cat-*.jar -h
```

## Configuration

The arguments can be provided in the following ways, ordered by priority:

* As a command line argument. 
* If no command line argument is given, an environment variable can be read. All environment variables must have a 
prefix `LNC_`, use uppercase characters are can have underscores. 
* If neither a command line argument nor a corresponding environment variable is provided, then the
configuration file would be read, if some has been defined. The path of the configuration file can be provided either
 with the `-c` command line flag or with the environment variable `LNC_CONFIG_FILE`. The property name matches the 
 environment variable name with lower case characters and dots instead of underscores. For example the environment 
variable `LNC_LOG_FILE` and the property `log.file` are related to the same argument.
* If no value is given for an argument, a default value will be used.

Use `-h` in order to get details about all available arguments.

## Changelogs

Versions are identified by both Git tags and the POM project version. The version information is also available from 
the JAR file manifest.

|_Version_|_Changes_|
|---|---|
|0.0|Initial version|
|1.0|Statistics sent to the standard output stream|
|2.0|Handling of alerts sent to the standard error stream|
|3.0|Use of curses in a terminal|

## Done

* Provide an help message with `-h` or `--help`
* Reading of a configuration file (although in a legacy _properties_ format) whose located can be given as an argument.
* Robustness to timezone changes (because the timezone offset is read while parsing)
* Configuration of the date-time pattern (can be change depending on *LogFileDateExt*) through a parameter
`DATE_TIME_FORMAT`. For now, the *LogFileDateExt* pattern can not be used, and the configuration supports only 
patterns defined according the [Java conventions](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#patterns).
* With a given count of possible sections, and a given configuration, the memory consumption is intended to be stable. 

## Todo

* Use a _Duration_ syntax for configuration argument instead of milliseconds counts.
* Use a _curses_ like library for console output
* Use a logging library like _logBack_
* Provide metrics about the bytes count.
* Provide stats in several periods of time like; _fromStart_, 1day, 1hour, 5min, 10sec
* Support a better configuration file format instead of the legacy _properties format_, like YAML for example
* Use alternative comparison for top sections (configuration based)
* Support multiple alerts. One of them may have different periods and thresholds. It requires probably a more complex
 syntax for the configuration file.
* Enhanced alert configuration based on metrics other than only the request throughput. It requires probably a more
complex syntax for the configuration file.
* Support of date parsing from most of `strftime()` patterns according to the *LogFileDateExt* access log 
configuration. The value of *LogFileDateExt* could be passed as a parameter and is meaningful since this is the 
parameter used for the access log formatting known from the user. It should be possible [in Java with some 
limitations](https://tomcat.apache.org/tomcat-4.1-doc/catalina/docs/api/org/apache/catalina/util/Strftime.html) to 
use a date-time formatting using the `strftime()` syntax. The 
access log configuration *LogTime* value is not important since we compute relative durations and absolute instants
 are not considered. 
* It should be noted, that if the format includes only the time, without the date for example, then the 
program would not be able to consider times before midnight from the next day. Likewise, the timezone offset should be 
   provided. If any time scope is missing, it would lead to wrong results. So we should make sure that the time is fully
  defined. It could be done when checking for the input date-time pattern.
* Bring more robustness about inputs not coming in the chronological order? 
* Makes possible to save/restore statistics so that a next start can retrieve the stats of a previous run.
* Have some persistence for gathered metrics 
* Rewrite this application in the *Rust language* in order to get better performance
* Build a lightweight HTTP API so that client programs can easily access the metrics. The HTTP API will need to support 
some real-time feature (like with HTTP-Streaming or Web-Socket) in order to notify alerts.
* Create some Web-based interface that consumes the HTTP API for a better user experience

## License

Copyright 2019 - Fabrice LARCHER (All rights reserved)
