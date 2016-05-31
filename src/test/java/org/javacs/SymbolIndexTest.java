package org.javacs;

import com.sun.tools.javac.code.Symbol;
import io.typefox.lsapi.*;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class SymbolIndexTest {
    @Test
    public void all() {
        Set<String> all = search("");

        assertThat(all, not(empty()));
    }

    @Test
    public void searchClasses() {
        Set<String> all = search("JLanguageServer");

        assertThat(all, hasItem("JavaLanguageServer"));
    }

    @Test
    public void searchMethods() {
        Set<String> all = search("gTextDocumentService");

        assertThat(all, hasItem("getTextDocumentService"));
    }

    private Set<String> search(String query) {
        return INDEX.search(query).stream().map(s -> s.getSimpleName().toString()).collect(Collectors.toSet());
    }

    private static final SymbolIndex INDEX = getIndex();
    
    private static SymbolIndex getIndex() {
        Set<Path> classPath = Collections.emptySet();
        Set<Path> sourcePath = Collections.singleton(Paths.get("src/main/java").toAbsolutePath());
        Path outputDirectory = Paths.get("out").toAbsolutePath();
        SymbolIndex index = new SymbolIndex(classPath, sourcePath, outputDirectory);

        index.initialIndexComplete.join();

        return index;
    }
}