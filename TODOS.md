# Todo

## Polish
* Autocomplete constructor signatures instead of just class name
* Add import code action
* Resolve methods with badly typed arguments
* Autocomplete is using the entire method signature
* Autocomplete method parameter types is returning classes twice
* Autocomplete is showing both override and super versions
* Autocomplete annotation fields
* Autocomplete inner classes
* Autocomplete members of container of anonymous class, from within anonymous class
* Hover exported methods and fields on annotations, interfaces, classes that are not on source path
* Hover shows javadoc ifa available
* Javadoc path for hover, autocomplete
* Remove from SymbolIndex on delete file

## Features 
* Go-to-subclasses
* Signature help

### Refactoring
* Inline method, variable
* Extract method, variable
* Replace for comprehension with loop

### Code generation
* New .java file class boilerplate
* Missing method definition
* Override method
* Add variable
* Enum options
* Cast to type
* Import missing file

### Code lens
* "N references" on method, class
* "N inherited" on class, with generate-override actions

## Optimizations
* Incremental parsing
* Only run attribution and flow phases on method of interest
* Kill java process when vscode quits
* Inactive org.javacs.Main process is grinding CPU

## Tests
* Hover info

## Lint
* Add 3rd-party linter (findbugs?)