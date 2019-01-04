
#!/bin/bash

# Needed if you have a java version other than 11 as default
JAVA_HOME=$(/usr/libexec/java_home -v 11)

# Compile the benchmark
mvn test-compile

# Emit the dependencies classpath
mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=scripts/classpath.txt

# Run the benchmark
java -cp $(cat scripts/classpath.txt):target/classes:target/test-classes --illegal-access=warn org.openjdk.jmh.Main BenchmarkPruner

# Clean up
rm scripts/classpath.txt