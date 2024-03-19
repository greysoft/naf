This file describes how to run the DNS batch resolver, a sample app that resolves a given set of DNS domains.
See sections 11 and 12 of the NAF Programmer's Guide for more info.

The DNS-BatchResolver is an example of a NAF application driven by a naf.xml style config file (./batchresolver.xml in this case) but primarily serves to illustrate how to use the NAF DNS API.
It takes an input file containing a list of domain names and issues DNS queries to resolve them, writing out the answers. It is capable of rapidly resolving very large datasets. 

BUILD
------
This sample app is built as part of the top-level NAF build. See README in repository root for how.
However you can also rebuild it from here as follows:
	mvn clean install

RUN
----
The supplied batchresolver.xml resolves a list of DNS domains of a specified type, which is specified in batchresolver.xml as MX (see 'dnstype' setting).
The list of DNS domains is in an input file, which is specified in batchresolver.xml as $HOME/infile.txt.
So you can create infile.txt with something as simple as the following 2 lines:
	gmail.com
	localhost
This means you are asking the batch-resolver to return the mailservers (MX RRs) for these two domains, and localhost should obviously return not-found.
See the NAF Programmer's Guide for the full config options.

Then run the batch resolver like this (replacing "dev-SNAPSHOT" with whatever version string is there, if different).
	cd samples/dns-batchresolver
	java -jar target/samples-dnsbatchresolver-dev-SNAPSHOT.jar -c batchresolver.xml

This will generate:
- The DNS resolution results in $HOME/outfile.txt
- A DNS-resolver cache dump in ./var/DNSdump-dnsbatch.txt
  (this is due to the 'exitdump' setting in batchresolver.xml)
- Logging output on stdout

LOGGING
--------
By default, this sample app will use NAF's native GreyLog logger directed to stdout, but it is also compiled with the SLF4J logger slf4j-logstdio, so if you wish to use that instead, you can run the app like this, to delegate all logging to slf4j-logstdio via SLF4J:
	java -Dgrey.logger.class=com.grey.logging.adapters.AdapterSLF4J -jar target/samples-dnsbatchresolver-VERSION.jar -c batchresolver.xml
