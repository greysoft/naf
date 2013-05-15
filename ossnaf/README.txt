Building From Source
=====================
NAF (Network Application Framework) is an open-source framework and the full source
code is available for download at:
	http://www.greyware.co.uk/naf
This includes the demo applications (without which their binaries are of limited
value).

If you have downloaded the binary distribution, skip to the next step

You can build this binary release from the source as follows:

1) Download and extract the source tree somewhere, which we'll call SRCROOT.
You will need to have the Java JDK (SE 6) and the Maven build tool on your path.

2) Run these commands from Unix shell. The DOS equivalent should be obvious.
- cd SRCROOT/build-common
- mvn install
This is only required the first time you build NAF (or after you clear out your Maven
repository), as it bootstraps the common POM defs into your Maven repository.

3) Run these commands from Unix shell.
- cd SRCROOT/ossnaf
- mvn -Dgrey.logger.level=WARN clean install
That's it! The system property setting shown is recommended to reduce the noise from the
unit tests.
The binary distributions will now be under SRCROOT/ossnaf/target, in both the ZIP and tar.gz
formats.

This corresponds to the pre-built binary distribution you could have downloaded, so continue
to the next step ...

     -------------------------------------------------------------

Contents of Binary Distribution
================================
A binary release of NAF is available in both the ZIP format or as a compressed tar file.
You "install" it simply by unpacking, and the extracted contents are:
- ./lib: The NAF library JARs
- ./samples: Pre-built binaries of the sample applications, along with
  suggested config files.
- ./docs: Documentation, both a Programmer's Guide and a Javadoc-style
  API reference.
- These text files (README and licencing info)

From now on, we'll refer to this root directory of the installed binary release
as NAFHOME.

     -------------------------------------------------------------

Demo Apps
==========
The simplest way to run the sample apps, is to copy their JARs into NAFHOME/lib
and their config files into NAFHOME/conf.
Then execute them from NAFHOME.

The DNS Batch Resolver (which is technically a built-in NAFlet rather than a
sample app) could be run by entering the following:
    java -jar lib/greynaf-2.3.0.jar -c conf/batchresolver.xml -logger dnsbatch < infile
This would write the results to stdout and log messages to ./dnsbatchresolver.log
See the NAF Programmer's Guide for more info about how to run the Batch Resolver.

The Port Forwarder sample app could be run as:
    java -jar lib/portfwd-2.3.0.jar -c conf/portfwd.xml -logger portfwd &
The logfile will be under ./var/logs

The Echo Bot could run a simple TCP test like this:
    java -Dgreynaf.dispatchers.logname=echobot -jar lib/echobot-2.3.0.jar -logger echobot -server-solo -clients 10:2 18001
Since this command creates multiple Dispatchers, which would normally use distinct
loggers that are named after them, the above command sets a system property that
directs them all to use the same GreyLog logger.

All the above apps use GreyLog for their logging, and if you experience any issues
with it, you can set the system property grey.logger.diagnostics=Y to get more
information about what's going on.
This can be done on the Java command-line, or put it in the grey.properties file, to
avoid having to continually type it.
