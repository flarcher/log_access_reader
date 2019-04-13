# HTTP access log reader exercise

## Program description

Requirements are:

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

## Run and usage


## Configuration

* The HTTP access log input file location (`/tmp/access.log` by default)
* Global statistics refresh period in seconds (defaulted to **10 seconds**)
* Alerting running duration for analysis in seconds (defaulted to **2 minutes**, that is 120 seconds)
* Alerting request count threshold in requests per second (defaulted to **10 req/sec**)


## How to build

* With Bash and Docker, just call `./build.sh`.
* With Docker only

    TODO

* Without either Bash of Docker, you would need:
    * Java Development Kit 8+
    * Maven 3+


    mvn install

## Future improvements

TODO

## License

(All rights reserved) Copyright 2019 - Fabrice LARCHER
