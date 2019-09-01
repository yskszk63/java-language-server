#!/bin/bash
# Download a copy of windows JDK in jdks/windows

set -e

# Download windows jdk
mkdir -p jdks/windows
cd jdks/windows
curl https://download.java.net/java/GA/jdk11/13/GPL/openjdk-11.0.1_windows-x64_bin.zip > windows.zip
unzip windows.zip
rm windows.zip
cd ../..