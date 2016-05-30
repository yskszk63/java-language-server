package org.javacs;

import io.typefox.lsapi.*;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class AutocompleteTest extends Fixtures {
    private static final Logger LOG = Logger.getLogger("main");

    @Test
    public void staticMember() throws IOException {
        String file = "/org/javacs/example/AutocompleteStaticMember.java";

        // Static method
        Set<String> suggestions = insertText(file, 4, 33);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "class"));
        assertThat(suggestions, not(hasItems("field", "method", "getClass")));
    }

    @Test
    @Ignore
    public void staticReference() throws IOException {
        String file = "/org/javacs/example/AutocompleteStaticReference.java";

        // Static method
        Set<String> suggestions = insertText(file, 2, 37);

        assertThat(suggestions, hasItems("methodStatic"));
        assertThat(suggestions, not(hasItems( "method", "new")));
    }

    @Test
    public void member() throws IOException {
        String file = "/org/javacs/example/AutocompleteMember.java";

        // Static method
        Set<String> suggestions = insertText(file, 4, 13);

        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "class")));
        assertThat(suggestions, hasItems("field", "method", "getClass"));
    }
    
    @Test
    public void order() throws IOException {
        String file = "/org/javacs/example/AutocompleteOrder.java";

        // Static method
        Set<String> suggestions = items(file, 4, 26).stream().map(i -> i.getSortText()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("0/getMethod()", "1/getInheritedMethod()", "2/getClass()"));
    }

    @Test
    public void other() throws IOException {
        String file = "/org/javacs/example/AutocompleteOther.java";

        // Static method
        Set<String> suggestions = insertText(file, 4, 33);

        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "class")));
        assertThat(suggestions, hasItems("field", "method", "getClass"));
    }

    @Test
    public void fromClasspath() throws IOException {
        String file = "/org/javacs/example/AutocompleteFromClasspath.java";

        // Static method
        Set<String> suggestions = items(file, 8, 17).stream().map(i -> i.getLabel()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("add(E)", "add(int, E)"));
    }

    @Test
    public void betweenLines() throws IOException {
        String file = "/org/javacs/example/AutocompleteBetweenLines.java";

        // Static method
        Set<String> suggestions = insertText(file, 8, 17);

        assertThat(suggestions, hasItems("add"));
    }

    @Test
    @Ignore
    public void reference() throws IOException {
        String file = "/org/javacs/example/AutocompleteReference.java";

        // Static method
        Set<String> suggestions = insertText(file, 2, 14);

        assertThat(suggestions, not(hasItems("methodStatic")));
        assertThat(suggestions, hasItems("method", "getClass"));
    }

    @Test
    public void docstring() throws IOException {
        String file = "/org/javacs/example/AutocompleteDocstring.java";

        Set<String> docstrings = documentation(file, 7, 14);

        assertThat(docstrings, hasItems("A method", "A field"));

        docstrings = documentation(file, 11, 31);

        assertThat(docstrings, hasItems("A fieldStatic", "A methodStatic"));
    }

    @Test
    public void classes() throws IOException {
        String file = "/org/javacs/example/AutocompleteClasses.java";

        // Static method
        Set<String> suggestions = insertText(file, 4, 9);

        assertThat(suggestions, hasItems("String", "SomeInnerClass"));
    }

    @Test
    public void editMethodName() throws IOException {
        String file = "/org/javacs/example/AutocompleteEditMethodName.java";

        // Static method
        Set<String> suggestions = insertText(file, 4, 20);

        assertThat(suggestions, hasItems("getClass"));
    }

    @Test
    public void restParams() throws IOException {
        String file = "/org/javacs/example/AutocompleteRest.java";

        // Static method
        Set<String> suggestions = items(file, 4, 17).stream().map(i -> i.getLabel()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("restMethod(String... params)"));
    }

    private Set<String> insertText(String file, int row, int column) throws IOException {
        List<? extends CompletionItem> items = items(file, row, column);

        return items
                .stream()
                .map(CompletionItem::getInsertText)
                .collect(Collectors.toSet());
    }

    private Set<String> documentation(String file, int row, int column) throws IOException {
        List<? extends CompletionItem> items = items(file, row, column);

        return items
                .stream()
                .flatMap(i -> {
                    if (i.getDocumentation() != null)
                        return Stream.of(i.getDocumentation().trim());
                    else
                        return Stream.empty();
                })
                .collect(Collectors.toSet());
    }

    private List<? extends CompletionItem> items(String file, int row, int column) {
        TextDocumentPositionParamsImpl position = new TextDocumentPositionParamsImpl();

        position.setPosition(new PositionImpl());
        position.getPosition().setLine(row);
        position.getPosition().setCharacter(column);
        position.setTextDocument(new TextDocumentIdentifierImpl());
        position.getTextDocument().setUri(uri(file).toString());

        JavaLanguageServer server = getJavaLanguageServer();

        return server.autocomplete(position).getItems();
    }

    private URI uri(String file) {
        try {
            return AutocompleteTest.class.getResource(file).toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
