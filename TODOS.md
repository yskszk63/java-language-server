# Todo

## Bugs
- Always shows last javadoc
- Make new file, rename, edit crashes compiler
- StringBuilder.length isn't autocompleting

## Autocomplete
- Annotation fields
- cc should match CamelCase
- Autocomplete POJO constructor This(T f, U g) { this.f = f; ... }

## Navigation
- Go-to-subclasses
- Test coverage codelens
- Go-to-definition for overriding methods
- Go-to-implementation for overridden methods

## Polish
- Show warning for unused local var, unused private method
- Use cached codelens during parse errors to prevent things from jumping around, or codelens-on-save
- Suppress references codelens for inherited methods
- Don't remove imports when there's an unresolved reference to that name

## JShell
- Support .jshell extension as "scratch pad"