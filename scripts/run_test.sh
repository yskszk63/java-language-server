#!/bin/bash

set -e

JAVA_HOME=`/usr/libexec/java_home -v 11`

mvn test -Dtest=$1#$2