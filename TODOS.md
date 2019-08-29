# Todo

## Autocomplete
- Autocomplete POJO constructor This(T f, U g) { this.f = f; ... }

## Navigation
- Go-to-subclasses
- Test coverage codelens
- Go-to-implementation for overridden methods
- `Thing#close()` shows 0 references for `try (thing)`
- Use package graph to limit search for find-usages/goto-def

## Bugs 
- Deleting file doesn't clear it from javac
- External delete causes find-references to crash because it's still in FileStore.javaSources()
- `return json.get("name").` doesn't auto-complete
- Restart debug test doesn't work
- Debugger doesn't remove breakpoints
- Find references actions accumulate and slow down responses
- Javac doesn't find protobuf classes in bazel