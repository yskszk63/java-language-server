# Todo

## Polish
* Remove definitions from context when source file is deleted
* Don't autocomplete inaccessible members
* Show inner classes as Outer.Inner
* Cannot find symbol errors getting reported twice
* Autocomplete with <>()
* Only issue no-config-file / not-on-sourcepath messages once

## Features 
* Open symbol by name
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

## Optimizations
* Incremental parsing
* Only run attribution and flow phases on method of interest
* Kill java process when vscode quits
* Inactive org.javacs.Main process is grinding CPU

## Tests
* Hover info