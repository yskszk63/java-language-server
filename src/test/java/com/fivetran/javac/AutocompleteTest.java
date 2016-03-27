package com.fivetran.javac;

import com.fivetran.javac.message.Position;
import com.fivetran.javac.message.RequestAutocomplete;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toSet;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class AutocompleteTest extends Fixtures {
    private static final Logger LOG = Logger.getLogger("");

    @Test
    public void staticMember() throws IOException {
        String file = "/AutocompleteStaticMember.java";

        // Static method
        Set<String> suggestions = autocomplete(file, 2, 33);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "class"));
        assertThat(suggestions, not(hasItems("field", "method", "getClass")));
    }

    @Test
    @Ignore
    public void staticReference() throws IOException {
        String file = "/AutocompleteStaticReference.java";

        // Static method
        Set<String> suggestions = autocomplete(file, 2, 37);

        assertThat(suggestions, hasItems("methodStatic"));
        assertThat(suggestions, not(hasItems( "method", "new")));
    }

    @Test
    public void member() throws IOException {
        String file = "/AutocompleteMember.java";

        // Static method
        Set<String> suggestions = autocomplete(file, 2, 13);

        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "class")));
        assertThat(suggestions, hasItems("field", "method", "getClass"));
    }

    @Test
    public void other() throws IOException {
        String file = "/AutocompleteOther.java";

        // Static method
        Set<String> suggestions = autocomplete(file, 2, 33);

        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "class")));
        assertThat(suggestions, hasItems("field", "method", "getClass"));
    }

    @Test
    @Ignore
    public void reference() throws IOException {
        String file = "/AutocompleteReference.java";

        // Static method
        Set<String> suggestions = autocomplete(file, 2, 14);

        assertThat(suggestions, not(hasItems("methodStatic")));
        assertThat(suggestions, hasItems("method", "getClass"));
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
