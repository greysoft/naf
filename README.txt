If you have downloaded the binary distribution, skip straight to Section B.

If you simply want to link NAF into your application, the Maven dependency block is as follows:
	<dependency>
		<groupId>com.github.greysoft.naf</groupId>
		<artifactId>greynaf</artifactId>
		<version>${project.version}</version>
	</dependency>

Section A - Building From Source
=================================
NAF (Network Application Framework) is an open-source framework for non-blocking sockets I/O and timers.
The source code is available for download at: http://www.greyware.co.uk/naf

Prerequisites:
- You will need to have the Java JDK (Java 8 or later) on your Path
- You will need to have the Maven build tool (3.5+) on your Path

You can build the binary release from the source as follows:
(we illustrate the procedure here with the Unix shell commands but the equivalent DOS commands should be obvious)

1) Extract the source tarball to a directory which we'll call SRCROOT.
Documentation is now available by pointing your browser at SRCROOT/docs/guide/index.htm

2) Run these commands from Unix shell.
- cd SRCROOT/ossnaf
- mvn -Dgrey.logger.level=WARN clean install
The system property setting shown is recommended to reduce the noise from the unit tests.

That's it!  The binary distributions will now be under SRCROOT/ossnaf/target, in both the ZIP and tar.gz formats
This corresponds to the pre-built binary distribution you could have downloaded, so continue to the next step ...

     -------------------------------------------------------------

Section B - Contents of Binary Distribution
============================================
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
