This file describes how to run PortFwd, which is a simple app to relay connections to configured backend services and serves as a basic demonstration of how to develop NAF applications.

This sample app is built as part of the top-level NAF build. See README in project root for how.
However you can also rebuild it from here as follows:
	mvn clean install

Replace "VERSION" in the commands below with the version you see embedded in the Jar name in the 'target' directory.
Note that dotted system properties transpose to upper-case environment variables with underscores, eg. -Dgrey.logger.configfile is equivalent to the env var GREY_LOGGER_CONFIGFILE=src/main/config/logging.xml

To use the supplied GreyLog config, you can run as either of these commands.
This creates a Dispatcher log under var/logs and a boot logger on stdout.
	java -Dgrey.logger.configfile=src/main/config/logging.xml -cp target/samples-portfwd-VERSION.jar com.grey.portfwd.App -c src/main/config/portfwd.xml
OR
	GREY_LOGGER_CONFIGFILE=src/main/config/logging.xml java -cp target/samples-portfwd-VERSION.jar com.grey.portfwd.App -c src/main/config/portfwd.xml

This sample app has been compiled with the SLF4J logger LogBack (see pom.xml) so if you want to send your logging output there instead of using NAF's GreyLog, then you can run the app like this instead:
	java -Dgrey.logger.class=com.grey.logging.adapters.AdapterSLF4J -cp target/samples-portfwd-VERSION.jar com.grey.portfwd.App -c src/main/config/portfwd.xml
Note that the same effect could be achieved with the above command, if you edited the logging.xml file to change the class attribute from NAF's LatinLogger to com.grey.logging.adapters.AdapterSLF4J.

If you browse to port 13000 on your localhost while PortFwd is running, you will see the NAFMAN monitoring screens (see NAF Guide for more info).
If you wish to specify an alternative port such as 14000, you can can simply add -Dgreynaf.baseport=14000 to the command line (or set GREYNAF_BASEPORT=14000)
