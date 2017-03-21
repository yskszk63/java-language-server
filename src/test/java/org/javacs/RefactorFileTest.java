package org.javacs;

import com.sun.tools.javac.tree.JCTree;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class RefactorFileTest {

    private static final Logger LOG = Logger.getLogger("main");
    public static final URI FAKE_FILE = URI.create("test/imaginary-resources/org/javacs/Example.java");

    @Test
    public void addImportToEmpty() {
        String before =
                "package org.javacs;\n" +
                "\n" +
                "public class Example { void main() { } }";
        List<TextEdit> edits = RefactorFile.addImport(file(before), "org.javacs", "Foo");
        String after = applyEdits(before, edits);

        assertThat(after, equalTo(
                "package org.javacs;\n" +
                        "\n" +
                        "import org.javacs.Foo;\n" +
                        "\n" +
                        "public class Example { void main() { } }"
        ));
    }

    @Test
    public void addImportToExisting() {
        String before =
                "package org.javacs;\n" +
                        "\n" +
                        "import java.util.List;\n" +
                        "\n" +
                        "public class Example { void main() { } }";
        List<TextEdit> edits = RefactorFile.addImport(file(before), "org.javacs", "Foo");
        String after = applyEdits(before, edits);

        assertThat(after, equalTo(
                "package org.javacs;\n" +
                        "\n" +
                        "import java.util.List;\n" +
                        "import org.javacs.Foo;\n" +
                        "\n" +
                        "public class Example { void main() { } }"
        ));
    }

    @Test
    public void importAlreadyExists() {
        String before =
                "package org.javacs;\n" +
                        "\n" +
                        "import java.util.List;\n" +
                        "\n" +
                        "public class Example { void main() { } }";
        List<TextEdit> edits = RefactorFile.addImport(file(before), "java.util", "List");
        String after = applyEdits(before, edits);

        assertThat(after, equalTo(
                "package org.javacs;\n" +
                        "\n" +
                        "import java.util.List;\n" +
                        "\n" +
                        "public class Example { void main() { } }"
        ));
    }

    @Test
    public void noPackage() {
        String before =
                "import java.util.List;\n" +
                        "\n" +
                        "public class Example { void main() { } }";
        List<TextEdit> edits = RefactorFile.addImport(file(before), "org.javacs", "Foo");
        String after = applyEdits(before, edits);

        assertThat(after, equalTo(
                "\n" +
                        "import java.util.List;\n" +
                        "import org.javacs.Foo;\n" +
                        "\n" +
                        "public class Example { void main() { } }"
        ));
    }

    @Test
    public void noPackageNoImports() {
        String before = "public class Example { void main() { } }";
        List<TextEdit> edits = RefactorFile.addImport(file(before), "org.javacs", "Foo");
        String after = applyEdits(before, edits);

        assertThat(after, equalTo(
                        "\n" +
                                "import org.javacs.Foo;\n" +
                        "\n" +
                        "public class Example { void main() { } }"
        ));
    }

    private String applyEdits(String before, List<TextEdit> edits) {
        StringBuffer buffer = new StringBuffer(before);

        edits.stream()
                .sorted(this::compareEdits)
                .forEach(edit -> applyEdit(buffer, edit));

        return buffer.toString();
    }

    private int compareEdits(TextEdit left, TextEdit right) {
        int compareLines = -Integer.compare(left.getRange().getEnd().getLine(), right.getRange().getEnd().getLine());

        if (compareLines != 0)
            return compareLines;
        else
            return -Integer.compare(left.getRange().getStart().getCharacter(), right.getRange().getEnd().getCharacter());
    }

    private void applyEdit(StringBuffer buffer, TextEdit edit) {
        buffer.replace(offset(edit.getRange().getStart(), buffer), offset(edit.getRange().getEnd(), buffer), edit.getNewText());
    }

    private int offset(Position pos, StringBuffer buffer) {
        return (int) JavaLanguageServer.findOffset(FAKE_FILE, Optional.of(buffer.toString()), pos.getLine(), pos.getCharacter());
    }

    private JCTree.JCCompilationUnit file(String content) {
        JavacHolder compiler = JavacHolder.createWithoutIndex(Collections.emptySet(), Collections.emptySet(), Paths.get("test-output"));
        ParseResult parsed = compiler.parse(FAKE_FILE, Optional.of(content));

        parsed.errors.getDiagnostics().forEach(err -> LOG.warning(err.toString()));

        return parsed.tree;
    }
}
