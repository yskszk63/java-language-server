package com.fivetran.javac;

import com.fivetran.javac.message.*;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class GotoTest extends Fixtures {
    private static final Logger LOG = Logger.getLogger("");
    private static final String file = "/Goto.java";

    @Test
    public void localVariable() throws IOException {
        Set<Location> suggestions = doGoto(file, 7, 8);

        assertThat(suggestions, contains(new Location("Goto.java", 2, 15, 2, 20)));
    }

    @Test
    public void defaultConstructor() throws IOException {
        Set<Location> suggestions = doGoto(file, 7, 20);

        assertThat(suggestions, contains(new Location("Goto.java", 0, 13, 0, 17)));
    }

    @Test
    public void constructor() throws IOException {
        Set<Location> suggestions = doGoto(file, 8, 20);

        assertThat(suggestions, contains(new Location("Goto.java", 27, 11, 27, 15)));
    }

    @Test
    public void className() throws IOException {
        Set<Location> suggestions = doGoto(file, 13, 8);

        assertThat(suggestions, contains(new Location("Goto.java", 0, 13, 0, 17)));
    }

    @Test
    public void staticField() throws IOException {
        Set<Location> suggestions = doGoto(file, 10, 21);

        assertThat(suggestions, contains(new Location("Goto.java", 33, 25, 33, 36)));
    }

    @Test
    public void field() throws IOException {
        Set<Location> suggestions = doGoto(file, 11, 21);

        assertThat(suggestions, contains(new Location("Goto.java", 34, 18, 34, 23)));
    }

    @Test
    public void staticMethod() throws IOException {
        Set<Location> suggestions = doGoto(file, 13, 13);

        assertThat(suggestions, contains(new Location("Goto.java", 35, 25, 35, 37)));
    }

    @Test
    public void method() throws IOException {
        Set<Location> suggestions = doGoto(file, 14, 13);

        assertThat(suggestions, contains(new Location("Goto.java", 38, 18, 38, 24)));
    }

    @Test
    public void staticMethodReference() throws IOException {
        Set<Location> suggestions = doGoto(file, 16, 26);

        assertThat(suggestions, contains(new Location("Goto.java", 35, 25, 35, 37)));
    }

    @Test
    public void methodReference() throws IOException {
        Set<Location> suggestions = doGoto(file, 17, 26);

        assertThat(suggestions, contains(new Location("Goto.java", 38, 18, 38, 24)));
    }

    @Test
    public void typeParam() throws IOException {
        Set<Location> suggestions = doGoto(file, 43, 11);

        assertThat(suggestions, contains(new Location("Goto.java", 0, 18, 0, 23)));
    }

    private Set<Location> doGoto(String file, int row, int column) throws IOException {
        RequestGoto request = new RequestGoto();

        request.path = path(file);
        request.text = new String(Files.readAllBytes(Paths.get(path(file))));
        request.position = new Position(row, column);
        request.config.sourcePath = Collections.singletonList("src/test/resources");

        return new Services().doGoto(request).definitions.stream()
                                                         .map(l -> new Location(simpleName(l.uri), l.range))
                                                         .collect(toSet());
    }

    private String simpleName(String uri) {
        return Paths.get(uri).getFileName().toString();
    }

    private String path(String file) {
        try {
            return GotoTest.class.getResource(file).toURI().getPath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
