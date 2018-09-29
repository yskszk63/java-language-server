# Todo

## Bugs
- import static foo.? doesn't auto-complete
- Workspace root as source path crashes compiler
- Nested source roots don't make sense
- @Override snippets include duplicates
- testMethod/testClass doesn't work for bazel

## Autocomplete
- Annotation fields
- cc should match CamelCase

## Navigation
- Go-to-subclasses

## Polish
- Convert {@tag ...} to `<tag>...</tag>` (see vscode-java)
- Auto-collapse imports

## Simplicity
- Use module-info.java instead of build files to figure out classpath
- Link a standalone executable with jlink (scripts/link.sh)

## JShell
- Support .jshell extension as "scratch pad"
