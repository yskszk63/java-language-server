package org.javacs;

import org.javacs.message.Location;
import org.javacs.message.Position;
import org.javacs.message.RequestGoto;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class GotoTest extends Fixtures {
    private static final Logger LOG = Logger.getLogger("main");
    private static final String file = "/org/javacs/example/Goto.java";
    private static final URI uri = uri(file);

    @Test
    public void localVariable() throws IOException {
        Set<Location> suggestions = doGoto(file, 9, 8);

        assertThat(suggestions, contains(new Location(uri, 4, 15, 4, 20)));
    }

    @Test
    public void defaultConstructor() throws IOException {
        Set<Location> suggestions = doGoto(file, 9, 20);

        assertThat(suggestions, contains(new Location(uri, 2, 13, 2, 17)));
    }

    @Test
    @Ignore // TODO
    public void constructor() throws IOException {
        Set<Location> suggestions = doGoto(file, 10, 20);

        assertThat(suggestions, contains(new Location(uri, 29, 11, 29, 15)));
    }

    @Test
    public void className() throws IOException {
        Set<Location> suggestions = doGoto(file, 15, 8);

        assertThat(suggestions, contains(new Location(uri, 2, 13, 2, 17)));
    }

    @Test
    public void staticField() throws IOException {
        Set<Location> suggestions = doGoto(file, 12, 21);

        assertThat(suggestions, contains(new Location(uri, 35, 25, 35, 36)));
    }

    @Test
    public void field() throws IOException {
        Set<Location> suggestions = doGoto(file, 13, 21);

        assertThat(suggestions, contains(new Location(uri, 36, 18, 36, 23)));
    }

    @Test
    public void staticMethod() throws IOException {
        Set<Location> suggestions = doGoto(file, 15, 13);

        assertThat(suggestions, contains(new Location(uri, 37, 25, 37, 37)));
    }

    @Test
    public void method() throws IOException {
        Set<Location> suggestions = doGoto(file, 16, 13);

        assertThat(suggestions, contains(new Location(uri, 40, 18, 40, 24)));
    }

    @Test
    public void staticMethodReference() throws IOException {
        Set<Location> suggestions = doGoto(file, 18, 26);

        assertThat(suggestions, contains(new Location(uri, 37, 25, 37, 37)));
    }

    @Test
    public void methodReference() throws IOException {
        Set<Location> suggestions = doGoto(file, 19, 26);

        assertThat(suggestions, contains(new Location(uri, 40, 18, 40, 24)));
    }

    @Test
    @Ignore // TODO
    public void typeParam() throws IOException {
        Set<Location> suggestions = doGoto(file, 45, 11);

        assertThat(suggestions, contains(new Location(uri, 2, 18, 2, 23)));
    }

    private Set<Location> doGoto(String file, int row, int column) throws IOException {
        RequestGoto request = new RequestGoto();

        request.path = path(file);
        request.text = new String(Files.readAllBytes(Paths.get(path(file))));
        request.position = new Position(row, column);

        return new Services(compiler).doGoto(request).definitions;
    }

    private static URI uri(String file) {
        try {
            return GotoTest.class.getResource(file).toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String path(String file) {
        return uri(file).getPath();
    }
}
