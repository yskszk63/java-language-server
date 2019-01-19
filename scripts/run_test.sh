#!/bin/bash

set -e

# Needed if you have a java version other than 11 as default
JAVA_HOME=$(/usr/libexec/java_home -v 11)

mvn test -Dtest=$1#$2