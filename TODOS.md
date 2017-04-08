# Todo

## Videos
* Add-import code action

## Bugs
* Auto-import in empty file generates extra line of WS
* Error squiggles are off
* Autocomplete is showing arg0, arg1 for constructors

## Polish
* Keep imports organized
* Autocomplete already-imported first
* Add import code action
* Resolve methods with badly typed arguments
* Hover shows javadoc if available
* Hover show FQ types if not imported
* Javadoc path for hover, autocomplete
* Status bar info during indexing

## Autocomplete
* Autocomplete constructor signatures instead of just class name
* Autocomplete is using the entire method signature
* Autocomplete annotation fields
* Autocomplete enum options in switch statement
* Autocomplete types in method args
* Class names not imported
  * Order by imported, name, use in sourcepath
* Signature help
* Other methods of class when we have already statically imported 1 method
* Constructor of scope-local class

## Features 
* Go-to-subclasses
* Signature help
* javaconfig.json sourcePath / sourcePathFile
* Reformat selection, file

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

## Optimizations
* Incremental parsing
* Only run attribution and flow phases on method of interest

## Tests
* Hover info

## Lint
* Add 3rd-party linter (findbugs?)