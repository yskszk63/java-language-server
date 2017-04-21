# Todo

## Bugs
* Signature help doesn't show for constructors

## Default configuration
* Instead of requiring javaconfig.json / pom.xml, infer a single forgiving configuration:
  * classpath = entire maven / gradle cache
  * sourcepath = inferred from package names in workspace
  * docpath = every .java file in workspace + src.zip + source jars in maven / gradle cache
  * outputDirectory = project-specific temp directory
* Instructions for how to download source jars and get javadoc via maven, gradle
* Support module-info.java as a way to limit autocomplete and provide compile-time 'symbol not found'

## Polish
* Status bar info during indexing

## Autocomplete
* Annotation fields
* Enum options in switch statement
* Other methods of class when we have already statically imported 1 method
* Interface name for anonymous class new Runnable() { }
* Order members stream inherited-last

## Features 
* Go-to-subclasses
* Reformat selection, file

## Code actions
* Explode import *
* Auto-add 'throws ?'
* Unused return foo() => String ? = foo()

### Refactoring
* Inline method, variable
* Extract method, variable
* Replace for comprehension with loop

### Snippets
* Collections
  * Map => Map<$key, $value> $name = new HashMap<>();
  * List => List<$value> $name = new ArrayList<>();
  * ...
* For loops
  * for => for ($each : $collection) { ... }
  * fori => for (int $i = 0; i < $until; i++) { ... }

### Code generation
* New .java file class boilerplate
* Missing method definition
* Override method
* Add variable
* Enum options
* Cast to type
* Import missing file
* Unused return value auto-add

### Code lens
* "N references" on method, class
* "N inherited" on class, with generate-override actions

## Lint
* Add 3rd-party linter (findbugs?)