#!/bin/bash
# Create self-contained copy of java in dist/mac

set -e

# Check JAVA_HOME points to correct java version
./scripts/check_java_home.sh

# Build using jlink
rm -rf dist/mac
$JAVA_HOME/bin/jlink \
  --add-modules java.base,java.compiler,java.logging,java.sql,java.xml,jdk.compiler,jdk.jdi,jdk.unsupported,jdk.zipfs \
  --output dist/mac \
  --no-header-files \
  --no-man-pages \
  --compress 2