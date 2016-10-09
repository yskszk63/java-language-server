package org.javacs;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import org.junit.Ignore;
import org.junit.Test;

import javax.tools.JavaFileObject;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class SymbolUnderCursorTest extends Fixtures {

    @Test
    public void classDeclaration() {
        assertEquals("SymbolUnderCursor", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 2, 21));
    }

    @Test
    public void fieldDeclaration() {
        assertEquals("field", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 3, 21));
    }

    @Test
    public void methodDeclaration() {
        assertEquals("method", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 5, 21));
    }

    @Test
    public void methodParameterDeclaration() {
        assertEquals("methodParameter", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 5, 35));
    }

    @Test
    public void localVariableDeclaration() {
        assertEquals("localVariable", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 6, 21));
    }

    @Test
    public void classIdentifier() {
        assertEquals("SymbolUnderCursor", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 11, 22));
    }

    @Test
    public void fieldIdentifier() {
        assertEquals("field", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 8, 26));
    }

    @Test
    public void methodIdentifier() {
        assertEquals("method", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 11, 11));
    }

    @Test
    public void methodSelect() {
        assertEquals("method", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 12, 16));
    }

    @Ignore // tree.sym is null
    @Test
    public void methodReference() {
        assertEquals("method", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 13, 45));
    }

    @Test
    public void methodParameterReference() {
        assertEquals("methodParameter", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 9, 31));
    }

    @Test
    public void localVariableReference() {
        assertEquals("localVariable", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 9, 15));
    }

    @Test
    public void constructorParameterDeclaration() {
        assertEquals("constructorParameter", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 16, 45));
    }

    private String symbolAt(String file, int line, int character) {
        Optional<Symbol> symbol = new JavaLanguageServer(compiler).findSymbol(compile(file), line, character);

        return symbol.map(s -> s.getSimpleName().toString()).orElse(null);
    }

    private JCTree.JCCompilationUnit compile(String path) {
        JavaFileObject file = new GetResourceFileObject(path);
        JCTree.JCCompilationUnit tree = compiler.parse(file);

        compiler.compile(Collections.singleton(tree));

        return tree;
    }

    private static JavacHolder compiler = newCompiler();

    private static JavacHolder newCompiler() {
        return new JavacHolder(Collections.emptySet(),
                               Collections.singleton(Paths.get("src/test/resources")),
                               Paths.get("out"));
    }
}
