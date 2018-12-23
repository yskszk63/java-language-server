# Todo

## Bugs
- Workspace root as source path crashes compiler
- Nested source roots don't make sense
- testMethod/testClass doesn't work for bazel
- Find-all-references doesn't work on constructors
- Files created in session don't autocomplete
- EnumMap default methods don't autocomplete

## Autocomplete
- Annotation fields
- cc should match CamelCase
- Detail of fields, vars should be type
- Autocomplete this.x = x; this.y = y; ... in constructor

## Navigation
- Go-to-subclasses

## Polish
- Convert {@tag ...} to `<tag>...</tag>` (see vscode-java)
- Auto-collapse imports
- Hover constructor should show constructor, not class
- String.format(...) coloring

## Simplicity
- Use module-info.java instead of build files to figure out classpath
- Link a standalone executable with jlink (scripts/link.sh)

## JShell
- Support .jshell extension as "scratch pad"

# Coloring
- new Foo< shouldn't make everything gree
- void f() shouldn't mess up next line as you type it
