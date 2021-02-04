If you simply want to link NAF into your application, then insert this Maven dependency block in your POM:
	<dependency>
		<groupId>com.github.greysoft.naf</groupId>
		<artifactId>greynaf</artifactId>
		<version>current version</version>
	</dependency>
You can then ignore sections A and B below.

Section A - Building NAF From Source
=====================================
NAF (Network Application Framework) is an open-source framework for non-blocking sockets I/O and timers.
The source code is available for download at: http://www.greyware.co.uk/naf

Prerequisites:
- You will need to have the Java JDK (Java 8 or later) on your Path
- You will need to have the Maven build tool (3.5+) on your Path

NAF is now available on GitHub, and can be built locally as follows:
- Clone https://github.com/greysoft/greybuild (contains required parent POM)
- Run this in greybuild project root: 
	mvn -Dgrey.logger.level=WARN clean install
- Clone https://github.com/greysoft/naf
- Run this in naf project root: 
	mvn -Dgrey.logger.level=WARN clean install
The logger setting shown is recommended to reduce the build noise.

The NAF jars and their dependencies will now all be under pkg/target/dependency and each individual Jar will also be under the 'target' directory of its own sub-project, eg. greylog/target, greynaf/target, etc.

The Javadocs will have been generated under pkg/target/site/apidocs

The two sample apps will also have been built, and their Jars can be found under:
- samples/echobot/target
- samples/portfwd/target

Finally the binaries will also have been zipped up along with the Javadocs, and are available in both Zip and compressed tar form:
- pkg/target/naf-VERSION.zip
- pkg/target/naf-VERSION.tar.gz

     -------------------------------------------------------------

Section B - Contents of Binary Distribution
============================================
NB: This section predates the publishing of NAF artifacts on the Maven Central repository, and used to provide a means of obtaining binary JARs to link against.
This is no longer the recommended way of linking the NAF library into your code. Use the Maven dependency block instead as shown above, to pull the libs from the Maven Central repository.

A binary release of NAF is available in both the ZIP format or as a compressed tar file.
You "install" it simply by unpacking, and the extracted contents are:
- ./lib: The NAF library JARs
- ./samples: Pre-built binaries of the sample applications, along with
  suggested config files.
- ./docs: Documentation, both a Programmer's Guide and a Javadoc-style
  API reference.
- These text files (README and licencing info)

From now on, we'll refer to this root directory of the installed binary release as NAFHOME.

     -------------------------------------------------------------

Section C - Demo Apps
======================
You will find the sample apps under NAFHOME/samples and the simplest way to run them is to copy their JARs into NAFHOME/lib and their config files to NAFHOME/conf. 
Then execute them from NAFHOME.

Note that if there is already a NAF application running on the standard NAFMAN port of 13000, you can specify an alternative port on the JVM command line thus:
	-Dgreynaf.baseport=13100

The DNS Batch Resolver (which is technically a built-in NAFlet rather than a sample app) could be run by entering the following:
    java -jar lib/greynaf-${project.version}.jar -c conf/batchresolver.xml -logger dnsbatch < infile
This would write the results to stdout and log messages to ./dnsbatchresolver.log
See the NAF Programmer's Guide for more info about how to run the Batch Resolver.
Note that the actual JAR filenames will obviously depend on the current version.

The Port Forwarder sample app could be run as:
    java -jar lib/samples-portfwd-${project.version}.jar -c conf/portfwd.xml -logger portfwd &
The logfile will be under ./var/logs

The Echo Bot could run a simple TCP test like this:
    java -Dgreynaf.dispatchers.logname=echobot -jar lib/samples-echobot-${project.version}.jar -logger echobot -server-solo -clients 10:2 18001
Since this command creates multiple Dispatchers, which would normally use distinct loggers that are named after them, the above command sets a system property that directs them all to use the same GreyLog logger.

All the above apps use GreyLog for their logging, and if you experience any issues with it, you can set the system property grey.logger.diagnostics=Y to get more information about what's going on.
This can be done on the Java command-line, or put it in the grey.properties file, to avoid having to continually type it.
