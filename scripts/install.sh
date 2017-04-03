# Installs locally
# You will need java, maven, vsce, and visual studio code to run this script
set -e

# Build fat jar
mvn package 

# Build vsix
vsce package -o build.vsix

# Install vsix
# Note that this will fail if you already have the plugin installed, in which case you should install it through the UI
code --install-extension build.vsix