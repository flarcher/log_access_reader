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

* In order to use _Docker_ (without having to install _Maven_ or _Java_), please run the script `build_docker.sh`. It will try to use your local _maven repository_ in `$HOME/.m2` so that subsequent builds will be faster.

```bash
./docker_build.sh
```

* If you have a _Java Development Kit 8+_ and _Maven 3+_ installed, you can also run the following command:

```bash
mvn clean install
```

## How to run

If the project has not been built yet, the scripts would build it first before to run it right away. But if a code update has been done, it would possibly use the binary that was previously built and not take the changes into account. 

### Using Docker

If you have _Docker_ installed, the fastest way is to run the application is using the following command: 

```bash
./docker_run.sh
```

It will assume that the access log file is located at `/tmp/access.log` on your host. For any other usage, you can build an image based on the `Dockerfile`:

```bash
docker build -t lnc .
docker run -it --rm -v /tmp:/tmp lnc
``` 

The second command in the example above should be adapted depending on your needs (command line arguments, environment variables, volumes, ...).

### Using the JRE

Otherwise, you would need a _Java Runtime Environment version 8+_ installed. With the `run.sh` script, you can provide any command line argument. In the example below the last argument `-h` aims at printing some help about the program usage. It can be replaced by other arguments. 

```bash
./run.sh -h
```

## Usage

Use `-h` as an argument in order to get details about all available arguments. It would output all command line flags, supported environment variables and property names. In this case, the program exists as soon as it printed the information.

Without `-h`, a terminal screen appears and prints:

* The overall metrics (accumulated since the first access log line to the latest)
* The latest metrics (duration the last 10 seconds by default)
* The alert events from the most recent to the oldest

You can quit the program anytime by pressing the _escape_ key or the _q_ key. 

All printed dates are in the ISO format. The metrics may be printed with SI symbols.

The alerting events printing follow the following patterns:

* When an alert is raised:

```
[${raisedate}]! RAISED ! "${description}" hits = {${value}} 
``` 

* When an alert is released:

```
[${releasedate}]!RELEASED! "${description}" hits = {${value}} since [${raisedate}] 
``` 

Here are some example of alerting events appearing on the screen:

```
[2018-05-09T18:00:41]! RAISED ! "High traffic" hits = {3}
[2018-05-09T18:00:44]!RELEASED! "High traffic" hits = {3} since [2018-05-09T18:00:41]
```

The alert events are also printed automatically to the standard output. You can then pipe the execution of the program so that the alerting events information get stored in an alert log file, like this:

```bash
./run.sh >> alerts.log
```

## Configuration

The arguments can be provided in the following ways, ordered by priority:

1. As a command line argument using a flag. E.g: `-f /tmp/access.log` (for configuration of the access log file path). 
2. If no command line argument is given for a configuration entry, a corresponding environment variable will be read. These environment variables have all a prefix `LNC_`. They use uppercase characters and can have underscores.
3. If neither a command line argument nor a corresponding environment variable is provided, then the configuration file would be read, if some has been defined. The path of the configuration file can be provided either with the `-c` command line flag or with the environment variable `LNC_CONFIG_FILE`. The property name matches the environment variable name, without the prefix, with lower case characters and with dots instead of underscores. For example the environment variable `LNC_LOG_FILE` and the property `log.file` are related to the same configuration entry.
4. If no value is given for a configuration entry, a default value will be used.

Here are some example of use:

* Use `-h` as an argument in order to get details about all available arguments. It also outputs all command line
 flags, used environment variables and property names. The program exists as soon as it printed the information.
* Use `-f <access_log_file>` in order to provide the location for the access log file to be read.
* Use `-l <threshold> -a <duration>` in order to update the default alerting configuration.

TODO: paste the `-h` result here

## Requirements

* Consume an actively written-to w3c-formatted HTTP access log. It should default to reading /tmp/access.log and be overrideable.  ✓
* Display stats every 10s about the traffic during those 10s: the sections of the web site with the most hits, as well as interesting summary statistics on the traffic as a whole. A section is defined as being what's before the second '/' in the resource section of the log line. For example, the section for "/pages/create" is "/pages" ✓
* Make sure a user can keep the app running and monitor the log file continuously ✓
* Whenever total traffic for the past 2 minutes exceeds a certain number on average, add a message saying that “High traffic generated an alert - hits = {value}, triggered at {time}”. The default threshold should be 10 requests per second, and should be overridable. ✓
* Whenever the total traffic drops again below that value on average for the past 2 minutes, add another message detailing when the alert recovered. ✓
* Make sure all messages showing when alerting thresholds are crossed remain visible on the page for historical reasons. ✓
* Write a test for the alerting logic. ✓
* Explain how you’d improve on this application design. ✓ _(please see below in this file)_
* If you have access to a linux docker environment, we'd love to be able to docker build and run your project! ✓ _(See script `docker_build.sh` and `docker_run.sh`)_ ✓

## Additional features

* Provide an help message with `-h` or `--help`
* Reading of a configuration file (although in a legacy _properties_ format) whose located can be given as an argument.
* Configuration of the date-time pattern through a parameter `DATE_TIME_FORMAT`. For now, the *LogFileDateExt* access log configuration value can not be used, and the configuration supports only patterns defined according the [Java conventions](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#patterns).
But the value can still be changed depending on *LogFileDateExt* value.
* Use of a _Duration_ syntax for configuration argument instead of a milliseconds count for example. Duration information in configuration entries can have values like `23m` (for 23 minutes) or `1m30s` (for one minute and 30 seconds).
* Display stats at a configurable rate for the latest metrics of a greater period of time. By default, we print each second the statistics about the last 10 seconds.
* The alert raising mechanism responsiveness does not depend on the time duration of the scan on which the alert thresholds are tested. The two durations can be configured through two separate arguments.
* There is a robustness/support related to timezone changes (because the timezone offset is read while parsing)
* The reading and processing mechanism is robust about inputs not coming in the chronological order and/or been late. Moreover, the entry processing follows a specific time clock (see class `ReaderClock`) that adapt its speed to the 
incoming lines (bound to time) of the access log file.
* For both overall and section related statistics, we display the request count and the byte count transfered.
* For both overall and section related statistics, we display the throughput per second and the bandwidth per second.
* Use a _curses like_ library for console output (in order to refresh overall and latest stats)
* Alerts events are sent to the standard output in a format that is easy to parse. It makes easy to log alerts from one program start to another.

## Technical remarks

* There is a simple thread-model (reader + display) handled by a single executor service that is managed in the `main` method of the `Main` class.
* The code makes possible to configure multiple alerts for various predicates (thresholds on any metrics) over different time durations and any mix of them. Configuration of alerting might still be complex for the user, so no configuration meaning is available in this version of the program. But still, if some requirement about alerting changes, we can already change the default (static) behavior easily.
* The code allows to easily use other comparison methods for the _top sections_ (currently using the request count for the comparison). It could be even made configurable (with some comparison method listing eventually).
* It uses aggregation of information in order to reduce memory usage and memory allocation. The gathered information related to a single access log (class `AccessLogLine`) has only a short-term live in the application. The `AccessLogLine` instances are not stored in any collection but are aggregated as soon as possible in classes implementing `Consumer<AccessLogLine>` being `StaticticAggregator` and `TimeBuckets`. These classes aggregate access log information and each instance is related to a whole range of time.
* With a given configuration, the memory consumption is intended to be stable over time. The 2-step aggregations (the first step running over the main idle time, then a second iterating for displayed statistics and alerts over 10 seconds by default) make sure to remove the oldest information (typically being more than 10s old with default configuration)
* The metrics aggregation mechanism is shared by the statistics display mechanism and the alerting. This can help the maintenance. It also lowers the memory usage (since a single instance a the aggregating class is used for both mechanisms). See the class `TimeBuckets` for more details.
* A limit about memory use can be configured. A possibly size-growing collection is related to the count of sections in statistics. A huge number of different sections in access log files can increase the memory consumption significantly. In order to address this, the arguments `MAX_SECTION_COUNT_RATIO` and `TOP_SECTION_COUNT` define a maximum count of sections held in memory, so that we can cap the memory consumption according to the gathering of _top sections statistics_.  The lower is the `MAX_SECTION_COUNT_RATIO` value, the bigger is the risk to miss some information related to the _top sections parts_ (however global statistics will stay true in all cases). About (only) the _top sections_ display, the trade-off between precision/correctness and memory consumption explains the reason why the parameter `MAX_SECTION_COUNT_RATIO` exists.
* The code makes possible to support many durations for watching latest statistics. The same is also true for alerting. Furthermore, only the longest duration impacts the memory usage, not the count of durations involved. The use of many durations is not supported by the configuration mean however.
* The alert configuration can be based on any metric (other than only the request throughput) and any condition. The class `AlertConfig` uses a `java.util.function.Predicate` instance for its definition, so that the code is flexible to any alert condition based on gathered data (represented by an instance of `Statistic`).

## Next steps

* Use a logging library like _logBack_ with an output towards a log file whose path can be configured.
* Provide JMX metrics. Especially about the maximum statictic's section count and the size of the time bucket's internal map.
* Makes possible to issue a configured command call when an alert is raised/released. 
* Provide stats in several periods of time like; _fromStart_, 1day, 1hour, 5min, 10sec
* Support a better configuration file format instead of the legacy _properties format_, like YAML for example
* Use alternative comparison for top sections (configuration based)
* Support multiple alerts configuration. The engine is already capable of handling many alerts with different periods and thresholds. But requires probably a more complex syntax for the configuration file.
* Use alternative conditions for alerts (with ou without threshold). It requires probably a more complex syntax for the configuration file.
* Support of date parsing from most of [`strftime()` patterns](https://www.systutorials.com/docs/linux/man/3-strftime/) according to the *LogFileDateExt* access log configuration.
The value of *LogFileDateExt* could be passed as a parameter and is meaningful since this is the parameter used for the access log formatting known from the user. It should be possible [in Java with some limitations](https://tomcat.apache.org/tomcat-4.1-doc/catalina/docs/api/org/apache/catalina/util/Strftime.html) to use a date-time formatting using the `strftime()` syntax. The access log configuration *LogTime* value is not important since we compute relative durations and absolute instants are not considered. 
* It should be noted, that if the format includes only the time, without the date for example, then the program would not be able to consider times before midnight from the next day. Likewise, the timezone offset should be provided. If any time scope is missing, it would lead to wrong results. So we should make sure that the time is fully defined. It could be done when checking for the input date-time pattern.
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
|4.0|Alert events sent to standard output and triggers some commands|

## License

Copyright 2019 - **Fabrice LARCHER** _(All rights reserved)_
