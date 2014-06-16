#!/bin/sh

CP=/tmp/cp.$$
rm -f ${CP}

mvn -q -DincludeScope=test -Dmdep.outputFile=${CP} dependency:build-classpath

echo "${PWD}/target/classes:${PWD}/target/test-classes:`cat ${CP}`"
