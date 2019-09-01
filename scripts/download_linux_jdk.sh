#!/bin/bash
# Download a copy of linux JDK in jdks/linux

set -e

# Download linux jdk
mkdir -p jdks/linux
cd jdks/linux
curl https://download.java.net/java/GA/jdk11/13/GPL/openjdk-11.0.1_linux-x64_bin.tar.gz > linux.tar.gz
gunzip -c linux.tar.gz | tar xopf -
rm linux.tar.gz
cd ../..