package org.javacs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;

import javax.lang.model.element.ElementKind;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CodeActions {
    private final JavacHolder compiler;
    private final URI file;
    private final Optional<String> textContent;
    private final JavacTask task;
    private final CompilationUnitTree source;

    CodeActions(JavacHolder compiler, URI file, Optional<String> textContent, int line, int character) {
        this.compiler = compiler;
        this.file = file;
        this.textContent = textContent;

        FocusedResult compile = compiler.compileFocused(file, textContent, line, character, false);

        this.task = compile.task;
        this.source = compile.compilationUnit;
    }

    private CompilationUnitTree pickFile(Iterable<? extends CompilationUnitTree> compiled, URI file) {
        List<CompilationUnitTree> list = Lists.newArrayList(compiled);

        assert list.size() == 1 : "Compiler produced multiple files " + list;

        return list.get(0);
    }

    public List<Command> find(CodeActionParams params) {
        return params.getContext().getDiagnostics().stream()
                .flatMap(diagnostic -> findCodeActionsForDiagnostic(diagnostic))
                .collect(Collectors.toList());
    }

    private Stream<Command> findCodeActionsForDiagnostic(Diagnostic diagnostic) {
        return codeActionsFor(diagnostic);
    }

    private Stream<Command> codeActionsFor(Diagnostic diagnostic) {
        if (diagnostic.getCode().equals("compiler.err.cant.resolve.location")) {
            return cannotFindSymbolClassName(diagnostic.getMessage())
                    .map(Stream::of).orElseGet(Stream::empty)
                    .flatMap(this::addImportActions);
        }
        else
            return Stream.empty();
    }

    /**
     * Search for symbols on the classpath and sourcepath that match name
     */
    private Stream<Command> addImportActions(String name) {
        Stream<Command> sourcePath = compiler.index.allSymbols(ElementKind.CLASS)
                .filter(symbol -> symbol.getName().equals(name))
                .map(symbol -> importClassCommand(symbol.getContainerName(), symbol.getName()));
        Stream<Command> classPath = compiler.classPathIndex.topLevelClasses(name, source.getPackageName().toString())
                .filter(c -> c.getSimpleName().equals(name))
                .map(c -> importClassCommand(c.getPackage().getName(), c.getSimpleName()));

        return Stream.concat(sourcePath, classPath);
    }

    private Command importClassCommand(String packageName, String className) {
        return new Command("Import " + packageName + "." + className, "Java.importClass", ImmutableList.of(file.toString(), packageName, className));
    }

    private static final Pattern CANNOT_FIND_SYMBOL = Pattern.compile("class (\\w+)");

    public static Optional<String> cannotFindSymbolClassName(String message) {
        Matcher matcher = CANNOT_FIND_SYMBOL.matcher(message);

        if (matcher.find())
            return Optional.of(matcher.group(1));
        else
            return Optional.empty();
    }
}
