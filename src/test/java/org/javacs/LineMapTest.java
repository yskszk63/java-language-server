package org.javacs;

import org.javacs.message.Position;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class LineMapTest extends Fixtures {
    @Test
    public void firstLine() throws URISyntaxException, IOException {
        Path path = Paths.get(ParserTest.class.getResource("/HelloWorld.java").toURI().getPath());
        Position found = LineMap.fromPath(path).point(0);

        assertThat(found, equalTo(new Position(0, 0)));

        found = LineMap.fromPath(path).point(5);

        assertThat(found, equalTo(new Position(0, 5)));
    }
}
