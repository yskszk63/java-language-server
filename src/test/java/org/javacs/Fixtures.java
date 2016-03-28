package org.javacs;

import org.junit.BeforeClass;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;

public class Fixtures {
    @BeforeClass
    public static void setup() throws IOException {
        LoggingFormat.startLogging();
    }

    protected static final JavacHolder compiler =
            new JavacHolder(Collections.emptyList(),
                            Collections.singletonList(Paths.get("src/test/resources")),
                            Paths.get("target"));
}
