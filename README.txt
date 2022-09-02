NAF (Network Application Framework) is an open-source framework for non-blocking sockets I/O and timers.
It is a Java API that implements a reactive event-driven framework based on the NIO interface, that supports non-blocking I/O over TCP, UDP and SSL.
It is distributed under the terms of the GNU Affero General Public License, Version 3 (AGPLv3). 

Home page: http://www.greyware.co.uk/naf
Programmer's Guide: docs/guide/index.htm in this repo
API Reference: http://www.greyware.co.uk/naf/install/docs/apidocs

If you simply want to link NAF into your application, then insert this Maven dependency block in your POM:
	<dependency>
		<groupId>com.github.greysoft.naf</groupId>
		<artifactId>greynaf</artifactId>
		<version>current version</version>
	</dependency>
You can then ignore sections A and B below.

Section A - Building NAF From Source
=====================================
Prerequisites:
- You will need to have the Java JDK (Java 8 or later) on your Path
- You will need to have the Maven build tool (3.8+) on your Path

NAF is available on GitHub, and can be built locally in these simple steps:

Step 1) Build grey-build-common, if needed
If the version of com.github.greysoft.greybuild:grey-build-common (contains required parent POM) referenced by this project (see root POM) has not yet been published on Maven Central, then you will have to install that locally before building this project.
The master branches of these two projects will always be in synch with each other.
Build grey-build-common as follows:
- Clone/download https://github.com/greysoft/greybuild to local workspace
- Run this in greybuild project root: 
	mvn clean install

Step 2) Build NAF
- Clone/download https://github.com/greysoft/naf to local workspace
- Run this in naf project root: 
	mvn -Dgrey.logger.level=WARN clean install
The logger setting shown is recommended to reduce the build noise.

After building NAF, its jars and their dependencies will all be under pkg/target/dependency and each individual Jar will also be under the 'target' directory of its own sub-project, eg. greylog/target, greynaf/target, etc.

The Javadocs will have been generated under pkg/target/site/apidocs

The sample apps will also have been built, and their Jars can be found under:
- samples/echobot/target
- samples/portfwd/target
- samples/dns-batchresolver/target

This build also creates a distribution package (in two formats):
- pkg/target/naf-VERSION.zip
- pkg/target/naf-VERSION.tar.gz

Section B - Contents of Distribution Package
==============================================
NB: This section predates the publishing of NAF artifacts on the Maven Central repository, and used to provide a means of obtaining binary JARs to link against.
This is no longer the recommended way of linking the NAF library into your code. Use the Maven dependency block instead as shown above, to pull the libs from the Maven Central repository.

The distribution package contains a binary release of NAF and is available in both the ZIP format or as a compressed tar file.
You "install" it simply by unpacking, and the extracted contents are:
- ./lib: The NAF library JARs
- ./samples: Pre-built binaries of the sample applications, along with
  suggested config files.
- ./docs: Documentation, both a Programmer's Guide and a Javadoc-style
  API reference.
- These text files (README and licencing info)

From now on, we'll refer to this root directory of the installed binary release as NAFHOME.

Section C - Demo Apps
======================
You will find the sample apps under NAFHOME/samples.
They each have a README file describing how to build and run and as explained above, their binaries are included in the distribution package.
