# Todo

## Polish
* Remove definitions from context when source file is deleted
* Don't autocomplete inaccessible members (see Resolve.isAccessible)
* Show inner classes as Outer.Inner
* Cannot find symbol errors getting reported twice
* Autocomplete with <>()
* Autocomplete constructor signatures instead of just class name
* Redo lint whenever typing stops for a couple seconds
* Find all source paths and initialize SymbolIndex for each
* Add import code action
* Resolve methods with badly typed arguments
* Autocomplete is using the entire method signature
* Autocomplete method parameter types is returning classes twice

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

## Optimizations
* Incremental parsing
* Only run attribution and flow phases on method of interest
* Kill java process when vscode quits
* Inactive org.javacs.Main process is grinding CPU

## Tests
* Hover info

## Lint
* Add 3rd-party linter (findbugs?)