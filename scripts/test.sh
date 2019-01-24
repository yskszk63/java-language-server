JAVA_HOME=$(/usr/libexec/java_home -v 11)

mvn test-compile

java \
    -cp $(cat target/cp.txt):$(pwd)/target/classes:$(pwd)/target/test-classes \
    --patch-module jdk.compiler=modules/jdk.compiler.jar \
    org.junit.runner.JUnitCore org.javacs.SimpleTest

# --upgrade-module-path /Users/georgefraser/Documents/jdk11/build/macosx-x86_64-normal-server-release/jdk/modules \