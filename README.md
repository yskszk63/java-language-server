# VS Code support for Java using the [Java Compiler API](https://docs.oracle.com/javase/7/docs/api/javax/tools/JavaCompiler.html)

Provides Java support using the Java Compiler API.
Requires that you have Java 8 installed on your system.

## Installation

[Install from the VS Code marketplace](https://marketplace.visualstudio.com/items?itemName=georgewfraser.vscode-javac)

## [Issues](https://github.com/georgewfraser/vscode-javac/issues)

## Features

### Javadoc

<img src="http://g.recordit.co/GROuFBSPQD.gif">

### Signature help

<img src="http://g.recordit.co/pXkdKptzrI.gif">

### Autocomplete symbols (with auto-import)

<img src="http://g.recordit.co/HpNZPIDA8T.gif">

### Autocomplete members

<img src="http://g.recordit.co/np8mXIWfQ8.gif">

### Go-to-definition

<img src="http://g.recordit.co/AJGsEVoF6z.gif">

### Find symbol

<img src="http://g.recordit.co/XuZvrCJfBx.gif">

### Lint

<img src="http://g.recordit.co/Fu8vgP0uG0.gif">

### Type information on hover

<img src="http://g.recordit.co/w5nRIfef65.gif">

### Code actions

<img src="http://g.recordit.co/pjQh1KuyK4.gif">

### Find references

<img src="http://g.recordit.co/3tNYL8StgJ.gif">

## Usage

VSCode will provide autocomplete and help text using:
* .java files anywhere in your workspace
* Java platform classes
* External dependencies specified using [settings](#Settings)

## Settings

We recommend you set the following in your [workspace settings](https://code.visualstudio.com/docs/getstarted/settings)

### java.externalDependencies

If you are using external dependencies, you should specify them in `.vscode/settings.xml` in the following format:

```json
{
    "java.externalDependencies": [
        // Maven format
        "junit:junit:jar:4.12:test",
        // Gradle-style format is also allowed
        "junit:junit:4.12"
    ]
}
```

Your build tools should be able to generate this list for you:

#### List maven dependencies:

```mvn dependency:list```

Look for entries like `[INFO]    junit:junit:jar:4.11:test`

#### List gradle dependencies

```gradle dependencies```

Look for entries like `\--- junit:junit:4.+ -> 4.12` or `\--- org.hamcrest:hamcrest-core:1.3`
You may have to do a little reformatting.

## javaconfig.json is depecated

Configuration using a `javaconfig.json` file in your workspace is deprecated; 
please switch to setting `java.externalDependencies` in `.vscode/settings.json` 

## Directory structure

### Java service process

A java process that does the hard work of parsing and analyzing .java source files.

    pom.xml (maven project file)
    src/ (java sources)
    repo/ (tools.jar packaged in a local maven repo)
    target/ (compiled java .class files, .jar archives)
    target/fat-jar.jar (single jar that needs to be distributed with extension)

### Typescript Visual Studio Code extension

"Glue code" that launches the external java process
and connects to it using [vscode-languageclient](https://www.npmjs.com/package/vscode-languageclient).

    package.json (node package file)
    tsconfig.json (typescript compilation configuration file)
    tsd.json (project file for tsd, a type definitions manager)
    lib/ (typescript sources)
    out/ (compiled javascript)

## Design

This extension consists of an external java process, 
which communicates with vscode using the [language server protocol](https://github.com/Microsoft/vscode-languageserver-protocol). 

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
We cache the .class files that are generated during compilation in a temporary folder,
and use those .class files instead of .java sources whenever they are up-to-date.

## Logs

The java service process will output a log file to stdout, which is visible using View / Output.

## Contributing

If you have npm and maven installed,
you should be able to install locally using 

    npm install -g vsce
    npm install
    ./scripts/install.sh