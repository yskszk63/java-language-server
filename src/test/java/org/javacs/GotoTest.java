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
    private static final String file = "/Goto.java";
    private static final URI uri = uri(file);

    @Test
    public void localVariable() throws IOException {
        Set<Location> suggestions = doGoto(file, 7, 8);

        assertThat(suggestions, contains(new Location(uri, 2, 15, 2, 20)));
    }

    @Test
    public void defaultConstructor() throws IOException {
        Set<Location> suggestions = doGoto(file, 7, 20);

        assertThat(suggestions, contains(new Location(uri, 0, 13, 0, 17)));
    }

    @Test
    @Ignore // TODO
    public void constructor() throws IOException {
        Set<Location> suggestions = doGoto(file, 8, 20);

        assertThat(suggestions, contains(new Location(uri, 27, 11, 27, 15)));
    }

    @Test
    public void className() throws IOException {
        Set<Location> suggestions = doGoto(file, 13, 8);

        assertThat(suggestions, contains(new Location(uri, 0, 13, 0, 17)));
    }

    @Test
    public void staticField() throws IOException {
        Set<Location> suggestions = doGoto(file, 10, 21);

        assertThat(suggestions, contains(new Location(uri, 33, 25, 33, 36)));
    }

    @Test
    public void field() throws IOException {
        Set<Location> suggestions = doGoto(file, 11, 21);

        assertThat(suggestions, contains(new Location(uri, 34, 18, 34, 23)));
    }

    @Test
    public void staticMethod() throws IOException {
        Set<Location> suggestions = doGoto(file, 13, 13);

        assertThat(suggestions, contains(new Location(uri, 35, 25, 35, 37)));
    }

    @Test
    public void method() throws IOException {
        Set<Location> suggestions = doGoto(file, 14, 13);

        assertThat(suggestions, contains(new Location(uri, 38, 18, 38, 24)));
    }

    @Test
    public void staticMethodReference() throws IOException {
        Set<Location> suggestions = doGoto(file, 16, 26);

        assertThat(suggestions, contains(new Location(uri, 35, 25, 35, 37)));
    }

    @Test
    public void methodReference() throws IOException {
        Set<Location> suggestions = doGoto(file, 17, 26);

        assertThat(suggestions, contains(new Location(uri, 38, 18, 38, 24)));
    }

    @Test
    @Ignore // TODO
    public void typeParam() throws IOException {
        Set<Location> suggestions = doGoto(file, 43, 11);

        assertThat(suggestions, contains(new Location(uri, 0, 18, 0, 23)));
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
