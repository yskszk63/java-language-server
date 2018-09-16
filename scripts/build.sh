#!/bin/bash

# Installs locally
# You will need java, maven, vsce, and visual studio code to run this script
set -e

export JAVA_HOME=`/usr/libexec/java_home -v 1.8`

# Needed once
npm install

# Build fat jar
mvn package -DskipTests

# Build vsix
vsce package -o build.vsix

echo 'Install build.vsix using the extensions menu'
