# Installs locally

# Compile typescript
tsc

# Build fat jar
mvn package 

# Ensure .vscode/extensions/vscode-javac exists
mkdir -p ~/.vscode/extensions/vscode-javac

# Copy runtime to .vscode/extensions/vscode-javac
cp -r ./out ~/.vscode/extensions/vscode-javac/out
cp -r ./node_modules ~/.vscode/extensions/vscode-javac/node_modules
cp ./package.json ~/.vscode/extensions/vscode-javac/package.json