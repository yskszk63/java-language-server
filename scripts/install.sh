# Installs locally
# You will need java, maven, vsce, and visual studio code to run this script
set -e

# Build fat jar
mvn package 

# Build vsix
vsce package -o build.vsix

echo 'Install build.vsix using the extensions menu'