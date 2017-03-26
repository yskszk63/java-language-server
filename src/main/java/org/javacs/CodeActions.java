package org.javacs;

//public class CodeActions {
//    private final JavacHolder compiler;
//    private final URI file;
//    private final Optional<String> textContent;
//    private final CompilationResult compiled;
//
//    public CodeActions(JavacHolder compiler, URI file, Optional<String> textContent) {
//        this.compiler = compiler;
//        this.file = file;
//        this.textContent = textContent;
//        this.compiled = compiler.compile(Collections.singletonMap(file, textContent));
//    }
//
//    public List<Command> find(CodeActionParams params) {
//        return params.getContext().getDiagnostics().stream()
//                .flatMap(diagnostic -> this.findCodeActionsForDiagnostic(diagnostic))
//                .collect(Collectors.toList());
//    }
//
//    private Stream<Command> findCodeActionsForDiagnostic(Diagnostic diagnostic) {
//        CompilationResult compile = compiler.compile(Collections.singletonMap(file, textContent));
//
//        return compile.errors.getDiagnostics().stream()
//                .filter(error -> matches(diagnostic, error))
//                .flatMap(error -> findCodeActionsForCompilerDiagnostic(compile, error));
//    }
//
//    private Stream<Command> findCodeActionsForCompilerDiagnostic(CompilationResult compile, javax.tools.Diagnostic<? extends JavaFileObject> error) {
//        return expressionIn(compile.trees, error.getStartPosition(), error.getEndPosition())
//                .map(x -> codeActionsFor(error, x))
//                .orElse(Stream.empty());
//    }
//
//    private Stream<Command> codeActionsFor(javax.tools.Diagnostic<? extends JavaFileObject> diagnostic, JCTree expression) {
//        String message = diagnostic.getMessage(Locale.US).replaceAll("[\\s\r\n]+", " ");
//
//        if (message.startsWith("cannot find symbol symbol: class"))
//            return asSymbol(expression).map(this::addImportActions).orElse(Stream.empty());
//        else
//            return Stream.empty();
//    }
//
//    /**
//     * Name of symbol, if expression looks like a symbol
//     */
//    private static Optional<String> asSymbol(JCTree expression) {
//        throw new UnsupportedOperationException();
//    }
//
//    /**
//     * Search for symbols on the classpath and sourcepath that match name
//     */
//    private Stream<Command> addImportActions(String name) {
//        return compiler.index.allSymbols(ElementKind.CLASS)
//                .filter(symbol -> symbol.getName().equals(name))
//                .map(this::importSymbolCommand);
//    }
//
//    private Command importSymbolCommand(SymbolInformation symbol) {
//        return new Command("Import " + symbol, "java.importClass", Collections.singletonList(symbol));
//    }
//
//    private Optional<JCTree> expressionIn(Collection<CompilationUnitTree> trees, long startPosition, long endPosition) {
//        return trees.stream()
//                .flatMap(tree -> stream(new RangeScanner(tree.getSourceFile(), startPosition, endPosition, compiler.context).findIn(tree)))
//                .findAny();
//    }
//
//    private boolean matches(Diagnostic find, javax.tools.Diagnostic<? extends JavaFileObject> candidate) {
//        return find.getCode().equals(candidate.getCode()) &&
//                overlaps(find, candidate);
//    }
//
//    private boolean overlaps(Diagnostic find, javax.tools.Diagnostic<? extends JavaFileObject> candidate) {
//        Range range = find.getRange();
//        long start = JavaLanguageServer.findOffset(file, textContent, range.getStart().getLine(), range.getStart().getCharacter());
//        long end = JavaLanguageServer.findOffset(file, textContent, range.getEnd().getLine(), range.getEnd().getCharacter());
//
//        return start <= candidate.getEndPosition() && candidate.getStartPosition() <= end;
//    }
//
//    private static <T> Stream<T> stream(Optional<T> option) {
//        return option.map(value -> Stream.of(value)).orElse(Stream.empty());
//    }
//}
