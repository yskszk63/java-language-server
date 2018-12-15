#!/bin/bash

set -e

# Set java version 11
JAVA_HOME=$(/usr/libexec/java_home -v 11)

# Figure out test classpath
mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=cp.txt

# Run all tests directly
./.circleci/test.sh
