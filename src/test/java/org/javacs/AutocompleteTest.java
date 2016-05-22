package org.javacs;

import org.javacs.message.Position;
import org.javacs.message.RequestAutocomplete;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class AutocompleteTest extends Fixtures {
    private static final Logger LOG = Logger.getLogger("main");

    @Test
    public void staticMember() throws IOException {
        String file = "/org/javacs/example/AutocompleteStaticMember.java";

        // Static method
        Set<String> suggestions = autocomplete(file, 4, 33);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "class"));
        assertThat(suggestions, not(hasItems("field", "method", "getClass")));
    }

    @Test
    @Ignore
    public void staticReference() throws IOException {
        String file = "/org/javacs/example/AutocompleteStaticReference.java";

        // Static method
        Set<String> suggestions = autocomplete(file, 2, 37);

        assertThat(suggestions, hasItems("methodStatic"));
        assertThat(suggestions, not(hasItems( "method", "new")));
    }

    @Test
    public void member() throws IOException {
        String file = "/org/javacs/example/AutocompleteMember.java";

        // Static method
        Set<String> suggestions = autocomplete(file, 4, 13);

        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "class")));
        assertThat(suggestions, hasItems("field", "method", "getClass"));
    }

    @Test
    public void other() throws IOException {
        String file = "/org/javacs/example/AutocompleteOther.java";

        // Static method
        Set<String> suggestions = autocomplete(file, 4, 33);

        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "class")));
        assertThat(suggestions, hasItems("field", "method", "getClass"));
    }

    @Test
    @Ignore
    public void reference() throws IOException {
        String file = "/org/javacs/example/AutocompleteReference.java";

        // Static method
        Set<String> suggestions = autocomplete(file, 2, 14);

        assertThat(suggestions, not(hasItems("methodStatic")));
        assertThat(suggestions, hasItems("method", "getClass"));
    }

    @Test
    public void docstring() throws IOException {
        String file = "/org/javacs/example/AutocompleteDocstring.java";

        // Static method
        RequestAutocomplete request = new RequestAutocomplete();

        request.path = path(file);
        request.text = new String(Files.readAllBytes(Paths.get(path(file))));
        request.position = new Position(7, 14);

        Set<String> docstrings = new Services(compiler)
                .autocomplete(request)
                .suggestions
                .stream()
                .flatMap(s -> s.documentation.map(Stream::of).orElse(Stream.empty()))
                .map(String::trim)
                .collect(toSet());

        assertThat(docstrings, hasItems("A method", "A field"));

        request.position = new Position(11, 31);

        docstrings = new Services(compiler)
                .autocomplete(request)
                .suggestions
                .stream()
                .flatMap(s -> s.documentation.map(Stream::of).orElse(Stream.empty()))
                .map(String::trim)
                .collect(toSet());

        assertThat(docstrings, hasItems("A fieldStatic", "A methodStatic"));
    }

    private Set<String> autocomplete(String file, int row, int column) throws IOException {
        RequestAutocomplete request = new RequestAutocomplete();

        request.path = path(file);
        request.text = new String(Files.readAllBytes(Paths.get(path(file))));
        request.position = new Position(row, column);

        return new Services(compiler).autocomplete(request).suggestions.stream().map(s -> s.insertText).collect(toSet());
    }

    private String path(String file) {
        try {
            return AutocompleteTest.class.getResource(file).toURI().getPath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
