# Todo

## Videos
* Signature help

## Bugs
* Error squiggles are off
* Go-to-def for enum

## Polish
* Javadoc path for hover, autocomplete
* Status bar info during indexing
* Re-lint after applying workspace edit

## Autocomplete
* Autocomplete annotation fields
* Autocomplete enum options in switch statement
* Other methods of class when we have already statically imported 1 method

## Features 
* Go-to-subclasses
* javaconfig.json sourcePath / sourcePathFile
* Reformat selection, file

## Code actions
* Explode import *
* Auto-add 'throws ?'
* foo() => String ? = foo()

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