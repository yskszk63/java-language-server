# Todo

## Bugs 
- Deleting file doesn't clear it from javac
- External delete causes find-references to crash because it's still in FileStore.javaSources()
- `return json.get("name").` doesn't auto-complete
- `return "foo"\n.` doesn't auto-complete
- Restart debug test doesn't work
- Javac doesn't find protobuf classes in bazel
- Replace <a href=...>text</a> with text in docs, see List.copyOf for example.
- Show 'not used' warnings for non-@Override package-private methods of private classes, because they can only be accessed from same file
- Package template of new package which is sibling of existing package shows sibling + leaf, not parent + leaf.
- `Thing#close()` shows 0 references for `try (thing)`
- Changing `class Foo {}` to `static class Foo {}` doesn't fix "non-static variables this" in `static void test() { new Foo() }`
- Fields don't show up in debugger

## Optimizations
- Compilation is very slow in the presence of lots of errors
- Use package graph to limit search for find-usages/goto-def

## Refactorings
- Quick fixes
    - Remove parameter
    - Prepend _ to parameter name
    - Delete LHS of variable declaration
    - Delete unused tree
    - Fix imports
    - Create method
- Selection
    - Extract constant
    - Extract variable
    - Extract method
    - Change package
    - Add parameter
    - Inline
- Code lens
    - Inherited methods