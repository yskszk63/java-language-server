package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.javacs.lsp.Diagnostic;
import org.junit.Before;
import org.junit.Test;

public class WarningsTest {
    protected static final Logger LOG = Logger.getLogger("main");

    private static List<String> errors = new ArrayList<>();

    protected static final JavaLanguageServer server =
            LanguageServerFixture.getJavaLanguageServer(WarningsTest::onError);

    private static void onError(Diagnostic error) {
        var string = String.format("%s(%d)", error.code, error.range.start.line + 1);
        errors.add(string);
    }

    @Before
    public void setup() {
        errors.clear();
    }

    @Test
    public void unusedLocal() {
        server.reportErrors(List.of(FindResource.uri("org/javacs/warn/Unused.java")));
        assertThat(errors, hasItem("unused(5)"));
    }
}
