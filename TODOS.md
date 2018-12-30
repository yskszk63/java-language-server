# Todo

## Bugs
- Workspace root as source path crashes compiler
- Nested source roots don't make sense
- testMethod/testClass doesn't work for bazel
- Find-all-references doesn't work on constructors
- Files created in session don't autocomplete
- EnumMap default methods don't autocomplete
- Crashes when you create the first file in a new maven project
- Deleted files remain in compiler, even when you restart (via classpath?)
- Always shows last javadoc
- Goto field goes to method of same name
- Goto definition goes to wrong method in overloads

## Autocomplete
- Annotation fields
- cc should match CamelCase
- Detail of fields, vars should be type
- Autocomplete POJO constructor This(T f, U g) { this.f = f; ... }
- Deprioritize Object fields

## Navigation
- Go-to-subclasses
- Test coverage codelens
- Go-to-definition for overriding methods
- Go-to-implementation for overridden methods

## Polish
- Convert {@tag ...} to `<tag>...</tag>` (see vscode-java)
- Hover constructor should show constructor, not class
- String.format(...) coloring
- `new` should be a control keyword, not a regular keyword
- Show warning for unused local var, unused private method
- Use cached codelens during parse errors to prevent things from jumping around, or codelens-on-save
- Suppress references codelens for inherited methods
- '1 reference' should just jump to definition instead of showing peek
- Don't remove imports when there's an unresolved reference to that name

## Simplicity
- Use module-info.java instead of build files to figure out classpath

## JShell
- Support .jshell extension as "scratch pad"

# Coloring
- new Foo< shouldn't make everything green
- void f() shouldn't mess up next line as you type it
- { on next line breaks coloring
