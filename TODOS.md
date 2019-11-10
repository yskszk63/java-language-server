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
- `return "foo"\n.` doesn't auto-complete
- Restart debug test doesn't work
- Javac doesn't find protobuf classes in bazel
- Replace <a href=...>text</a> with text in docs, see List.copyOf for example.
- When no overload is matched, go-to all definitions of method name
- Show 'not used' warnings for non-@Override package-private methods of private classes, because they can only be accessed from same file
- Package template of new package which is sibling of existing package shows sibling + leaf, not parent + leaf.

## Features
- Lint unused args when method isn't overloading something

## Optimizations
- Compilation is very slow in the presence of lots of errors