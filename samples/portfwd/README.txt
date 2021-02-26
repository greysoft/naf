This file describes how to run PortFwd, a simple demo app that relays connections to configured backend services.
See sections 11 and 12 of the NAF Programmer's Guide for more info.

PortFwd is an example of a NAF application driven by a naf.xml style config file (src/main/config/portfwd.xml in this case) and it serves as a basic demonstration of how to develop NAF applications, with listeners, timers and a custom NAFMAN command.
We also illustrate here how NAF's native GreyLog logging can be redirected to a standard SLF4J logger.

BUILD AND RUN
--------------
This sample app is built as part of the top-level NAF build. See README in repository root for how.
However you can also rebuild it from here as follows:
	mvn clean install

When running PortFwd, replace "VERSION" in all the commands below with the version you see embedded in the Jar name in the 'target' directory.
Note that dotted system properties transpose to upper-case environment variables with underscores, eg. -Dgrey.logger.configfile is equivalent to the env var GREY_LOGGER_CONFIGFILE=src/main/config/logging.xml

Run this to get basic usage:
	java -cp target/samples-portfwd-VERSION.jar com.grey.portfwd.App -help

Although this README tends to use the the classpath-and-main-class invocation syntax shown above, the PortFwd Jar is executable and can be run more simply like this:
	java -jar target/samples-portfwd-VERSION.jar -help

The portfwd.xml config file specifies that the two relay services should listen on ports 18001 and 18002, but note that these are default values which can be overridden by the environment variables you can see in the config.

LOGGING
--------
To use NAF's native GreyLog logger with the supplied GreyLog config, you can run as either of these commands. They create a Dispatcher log under var/logs and a boot logger on stdout.
	java -Dgrey.logger.configfile=src/main/config/logging.xml -cp target/samples-portfwd-VERSION.jar com.grey.portfwd.App -c src/main/config/portfwd.xml
OR
	GREY_LOGGER_CONFIGFILE=src/main/config/logging.xml java -cp target/samples-portfwd-VERSION.jar com.grey.portfwd.App -c src/main/config/portfwd.xml

If you do not specify any logging config at all, NAF applications will use a native GreyLog logger which is directed to stdout.
	java -cp target/samples-portfwd-VERSION.jar com.grey.portfwd.App -c src/main/config/portfwd.xml

To get diagnostic logging on the logging setup itself, set -Dgrey.logger.diagnostics=true on the command line (or set the env var GREY_LOGGER_DIAGNOSTICS=true).

INTEGRATION WITH SLF4J LOGGERS
-------------------------------
This sample app has been compiled with the SLF4J logger LogBack (see pom.xml) so if you want to do your logging via LogBack instead of using NAF's native GreyLog logger, then you can run the app like this, to delegate all logging to SLF4J.
	java -Dgrey.logger.class=com.grey.logging.adapters.AdapterSLF4J -cp target/samples-portfwd-VERSION.jar com.grey.portfwd.App -c src/main/config/portfwd.xml

Note that the same effect could be achieved with the logging.xml config file, if you changed the 'class' attribute from NAF's LatinLogger to com.grey.logging.adapters.AdapterSLF4J.

NAFMAN
--------
If you browse to port 13000 on your localhost while PortFwd is running, you will see the NAFMAN monitoring screens (see NAF Guide for more info).
If you wish to specify an alternative port such as 14000, you can can simply add -Dgreynaf.baseport=14000 to the command line (or set the env var GREYNAF_BASEPORT=14000).

The NAFMAN screen shows a list of available commands at the bottom, which consist of a set of generic NAFMAN commands plus the bespoke SHOWCONNS command that was registered by this sample app.
You can click on any of the commands to execute them and see their output.

You can also execute NAFMAN commands from the command line.
This runs the SHOWCONNS command, which  display the current set of relayed connections:
	java -cp target/samples-portfwd-VERSION.jar com.grey.portfwd.App -remote localhost:13000 -cmd showconns?nohttp=y
This runs a generic NAFMAN command to show the available NAFMAN commands:
	java -cp target/samples-portfwd-VERSION.jar com.grey.portfwd.App -remote localhost:13000 -cmd showcmds?nohttp=y

You can also use the executable NAF jar to run NAFMAN commands. Run this command from the root of the repo.
	java -jar pkg/target/dependency/greynaf-dev-VERSION.jar -remote localhost:13000 -cmd showconns
