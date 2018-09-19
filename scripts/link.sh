#!/bin/bash
# Work-in-progress! 
# This script tries to link everything into a self-contained executable using jlink.
# It doesn't yet work because our dependencies aren't modularized

set -e

# Needed once
npm install

# Build jar
mvn package -DskipTests
# Copy dependencies
rm -rf target/deps
mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/deps
# Copy class files
mkdir -p target/mods
mv target/classes target/mods/javacs
# Build using jlink
jlink \
  --module-path target/mods:target/deps \
  --add-modules javacs \
  --launcher javacs=javacs/org.javacs.Main \
  --output dist \
  --strip-debug \
  --compress 2 \
  --no-header-files \
  --no-man-pages

# Build vsix
vsce package -o build.vsix

echo 'Install build.vsix using the extensions menu'
