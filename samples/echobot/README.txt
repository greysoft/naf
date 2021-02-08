This file describes how to run EchoBot, a simple demo app that sends or echoes TCP and UDP data.
See sections 12 and 13 of the NAF Programmer's Guide for more info.

EchoBot is an example of a NAF application that programatically creates Dispatccher and Listeners as opposed to the other sample app, PortFwd, which is entirely config-driven.
We also illustrate here how standard SLF4J logs can be redirected to NAF's native GreyLog logging (which is the opposite redirection to that demonstrated in PortFwd).

BUILD AND RUN
--------------
This sample app is built as part of the top-level NAF build. See README in repository root for how.
However you can also rebuild it from here as follows:
	mvn clean install

When running EchoBot, replace "VERSION" in all the commands below with the version you see embedded in the Jar name in the 'target' directory.
Note that dotted system properties transpose to upper-case environment variables with underscores, eg. -Dgrey.logger.configfile is equivalent to the env var GREY_LOGGER_CONFIGFILE=src/main/config/logging.xml

Run this to get basic usage:
	java -jar target/samples-echobot-VERSION.jar -help

Unlike the PortFwd sample app, EchoBot has no config files at all. It doesn't have a NAF config file because it sets up the Dispatchers (and optionally NAFMAN too) programmatically and it doesn't have any logging config as it relies on the default settings, with all output going to stdout.

EchoBot runs one or more client instances against an echo server, each sending it some fixed-length messages, and at the end it prints latency and throughput stats on the echo request/response.

Example commands:
This runs the echo server on port 14000 in one Dispatcher and a client in another
	java -jar target/samples-echobot-*.jar -server-solo -clients 1 14000

This runs the server and client code in the same EchoBot, with 2 client instances sending the test messages.
	java -jar target/samples-echobot-*.jar -server -clients 1:2 14000

This does the same as the above, but using UDP rather than TCP and with each client sending 2 8KB messages, rather than the default of 1 4KB message.
	java -jar target/samples-echobot-*.jar -udp -server -clients 1:2 -msg 2:8192 14000

LOGGING
--------
The main point about the logging in this sample app, is to demonstrate how SLF4J logs can be redirected to NAF's native GreyLog loggers.
Unlike the reverse redirection which is illustrated in PortFwd, this does not require any config settings. The redirection of SLF4J to GreyLog happens simply byt virtue of adding greylog-slf4j to our classpath (see the pom.xml build script) which is an SLF4J adapter that routes all logging to GreyLog.

As with PortFwd, you can obtain additional logging diagnostics by specifying -Dgrey.logger.diagnostics=true on the command line (or setting the env var GREY_LOGGER_DIAGNOSTICS=true).

NAFMAN
-------
This sample app does not run NAFMAN by default, but if you set the system property -Dgrey.echobot.nafman=y on the command (or the env var GREY_ECHOBOT_NAFMAN=y) then it will enable NAFMAN, and you canm browse to port 13000 on the local host while EchoBot is running.
Of course EchoBot normally runs for only an instant, but if you want time to examine its NAFMAN, you can run it in server-only mode, in which case it simply hangs waiting for input.
	java -jar target/samples-echobot-*.jar -server 14000
