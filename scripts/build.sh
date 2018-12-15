#!/bin/bash

# Installs locally
# You will need java, maven, vsce, and visual studio code to run this script
set -e

# Needed if you have a java version other than 11 as default
JAVA_HOME=$(/usr/libexec/java_home -v 11)

# Needed once
npm install

# Build fat jar
mvn package -DskipTests

# Build vsix
vsce package -o build.vsix

code --install-extension build.vsix

echo 'Reload VSCode to update extension'
