# Todo

## Bugs
- import static foo.? doesn't auto-complete
- Workspace root as source path crashes compiler

## Autocomplete
- NameOfClass... default constructor initializing final fields
- Annotation fields
- cc should match CamelCase

## Navigation
- Go-to-subclasses

## Code generation
- Auto-add 'throws ?'
- New .java file class boilerplate
- Missing method definition
- Use var types to disambiguate imports
- Generate catch clauses somehow
- Implement error-prone [patch](https://errorprone.info/docs/patching)
- Custom refaster rules

## Polish
- Status bar during startup
- Convert {@tag ...} to `<tag>...</tag>` (see vscode-java)
- Semantic coloring beta feature

## Simplicity
- Use module-info.java instead of build files to figure out classpath
- Link a standalone executable with jlink (scripts/link.sh)

## JShell
- Support .jshell extension as "scratch pad"
