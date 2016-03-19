# VS Code support for Java using the javac API

Provides Java support using the javac API.

## Features

* Compile-on-save

## javaconfig.json

The presence of a `javaconfig.json` file indicates that this directory is the root of a Java source tree.

### Examples

### Compile without a build tool

Set the source and class path:

    {
        "sourcePath": ["src"],
        "classPath": ["lib/MyJar.jar"],
        "outputDirectory": "out"
    }

### Compile using maven

`javaconfig.json`

Set the source path, and get the class path from a file:

    {
        "sourcePath": ["src/main/java"],
        "classPathFile": "classpath.txt",
        "outputDirectory": "target"
    }

`pom.xml`

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

`gitignore.txt`

Ignore `classpath.txt`, since it will be different on every host

    classpath.txt
    ...
    
