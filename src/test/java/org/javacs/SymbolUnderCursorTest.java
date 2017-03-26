package org.javacs;

import org.junit.Ignore;
import org.junit.Test;

import javax.lang.model.element.Element;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class SymbolUnderCursorTest {

    /* symbolAt doesn't work on declarations anymore and I don't think it should
    @Test
    public void classDeclaration() {
        assertEquals("SymbolUnderCursor", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 3, 22));
    }

    @Test
    public void fieldDeclaration() {
        assertEquals("field", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 4, 22));
    }

    @Test
    public void methodDeclaration() {
        assertEquals("method", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 6, 22));
    }

    @Test
    public void methodParameterDeclaration() {
        assertEquals("methodParameter", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 6, 36));
    }

    @Test
    public void localVariableDeclaration() {
        assertEquals("localVariable", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 7, 22));
    }

    @Test
    public void constructorParameterDeclaration() {
        assertEquals("constructorParameter", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 17, 46));
    }
    */

    @Test
    public void classIdentifier() {
        assertEquals("SymbolUnderCursor", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 12, 23));
    }

    @Test
    public void fieldIdentifier() {
        assertEquals("field", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 9, 27));
    }

    @Test
    public void methodIdentifier() {
        assertEquals("method", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 12, 12));
    }

    @Test
    public void methodSelect() {
        assertEquals("method", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 13, 17));
    }

    @Ignore // tree.sym is null
    @Test
    public void methodReference() {
        assertEquals("method", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 14, 46));
    }

    @Test
    public void methodParameterReference() {
        assertEquals("methodParameter", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 10, 32));
    }

    @Test
    public void localVariableReference() {
        assertEquals("localVariable", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 10, 16));
    }

    private String symbolAt(String file, int line, int character) {
        Optional<Element> symbol = new JavaLanguageServer(compiler).findSymbol(FindResource.uri(file), line, character);

        return symbol.map(s -> s.getSimpleName().toString()).orElse(null);
    }

    private static JavacHolder compiler = newCompiler();

    private static JavacHolder newCompiler() {
        return JavacHolder.createWithoutIndex(
                Collections.emptySet(),
                Collections.singleton(Paths.get("src/test/resources")),
                Paths.get("out")
        );
    }
}
