# VS Code support for Java using the javac API

Provides Java support using the javac API.

## Features

* Lint
* Autocomplete
* Go-to-definition

## javaconfig.json

The presence of a `javaconfig.json` file indicates that this directory is the root of a Java source tree.

### Examples

### Compile using maven

#### javaconfig.json

Set the source path, and get the class path from a file:

    {
        "sourcePath": ["src/main/java"],
        "classPathFile": "classpath.txt",
        "outputDirectory": "target"
    }

#### pom.xml

Configure maven to output `classpath.txt`

    <project ...>
        ...
        <build>
            ...
            <plugins>
                ...
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-dependency-plugin</artifactId>
                    <version>2.9</version>
                    <executions>
                        <execution>
                            <id>build-classpath</id>
                            <phase>generate-sources</phase>
                            <goals>
                                <goal>build-classpath</goal>
                            </goals>
                        </execution>
                    </executions>
                    <configuration>
                        <outputFile>classpath.txt</outputFile>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </project>

#### .gitignore

Ignore `classpath.txt`, since it will be different on every host

    classpath.txt
    ...
    
## Structure

### Java service process

A java process that does the hard work of parsing and analyzing .java source files.

    pom.xml (maven project file)
    src/ (java sources)
    repo/ (tools.jar packaged in a local maven repo)
    target/ (compiled java .class files, .jar archives)
    target/fat-jar.jar (single jar that needs to be distributed with extension)

### Typescript Visual Studio Code extension

"Glue code" that connects to the external java process using a network port,
and implements various features of the Visual Studio Code extension API.

    package.json (node package file)
    tsconfig.json (typescript compilation configuration file)
    tsd.json (project file for tsd, a type definitions manager)
    lib/ (typescript sources)
    out/ (compiled javascript)

## Design

This extension consists of an external java process,
and some typescript glue code that talks to the VS Code API.
The tyescript glue code communicates with the external java process using a randomly assigned network port.

### Java service process

The java service process uses the implementation of the Java compiler in tools.jar, 
which is a part of the JDK.
When VS Code needs to lint a file, perform autocomplete, 
or some other task that requires Java code insight,
the java service process invokes the Java compiler programatically,
then intercepts the data structures the Java compiler uses to represent source trees and types.

### Incremental updates

The Java compiler isn't designed for incremental parsing and analysis.
However, it is *extremely* fast, so recompiling a single file gives good performance,
as long as we don't also recompile all of its dependencies.
We accomplish this by maintaining a single copy of the Java compiler in memory at all times.
When we want to recompile a file, 
we clear that *one* file from the internal caches of the Java compiler,
and then rerun the compiler.