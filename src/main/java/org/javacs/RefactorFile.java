package org.javacs;

import com.google.common.collect.Lists;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.Pretty;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

class RefactorFile {
    public static List<TextEdit> addImport(CompilationUnitTree sourceTree, String packageName, String className) {
        // TODO switch to public APIs
        JCTree.JCCompilationUnit source = (JCTree.JCCompilationUnit) sourceTree;

        Objects.requireNonNull(packageName, "Package name is null");
        Objects.requireNonNull(className, "Class name is null");

        if (alreadyImported(source, packageName, className))
            return Collections.emptyList();

        Position lastExistingImport = endOfImports(source);
        Position zero = new Position(0, 0);
        String afterString = organizeImports(source, packageName, className);

        if (lastExistingImport.equals(zero))
            afterString += "\n";

        return Collections.singletonList(new TextEdit(new Range(zero, lastExistingImport), afterString));
    }

    private static Position endOfImports(JCTree.JCCompilationUnit source) {
        Position zero = new Position(0, 0);
        Position endOfPackage = Optional.ofNullable(source.pid)
                .map(p -> findEndOfLine(source.getSourceFile(), p.getStartPosition()))
                .orElse(zero);
        return source.defs.stream()
                .filter(RefactorFile::isImportSection)
                .max(RefactorFile::comparePosition)
                .map(def -> def.getStartPosition())
                .map(offset -> findEndOfLine(source.getSourceFile(), offset))
                .orElse(endOfPackage);
    }

    /**
     * Convert on offset-based position to a {@link Position}
     */
    public static Position findEndOfLine(JavaFileObject file, long findOffset) {
        try (Reader in = file.openReader(true)) {
            long offset = 0;
            int line = 0;
            int character = 0;

            // Find the start position
            while (offset < findOffset) {
                int next = in.read();

                if (next < 0)
                    break;
                else {
                    offset++;
                    character++;

                    if (next == '\n') {
                        line++;
                        character = 0;
                    }
                }
            }

            while (true) {
                int next = in.read();

                if (next < 0)
                    break;
                else {
                    offset++;
                    character++;

                    if (next == '\n')
                        break;
                }
            }

            return new Position(line, character);
        } catch (IOException e) {
            throw ShowMessageException.error(e.getMessage(), e);
        }
    }

    private static boolean alreadyImported(JCTree.JCCompilationUnit source, String packageName, String className) {
        return source.getImports()
                    .stream()
                    .anyMatch(i -> importEquals(i, packageName, className));
    }

    private static String organizeImports(JCTree.JCCompilationUnit source, String packageName, String className) {
        Context context = new Context();
        JavacFileManager fileManager = new JavacFileManager(context, true, null);
        Names names = Names.instance(context);
        TreeMaker factory = TreeMaker.instance(context);
        List<JCTree.JCImport> imports = Lists.newArrayList(source.getImports());

        imports.add(factory.Import(
                factory.Select(
                        factory.Ident(names.fromString(packageName)),
                        names.fromString(className)),
                false
        ));

        JCTree.JCCompilationUnit after = factory.TopLevel(
                source.packageAnnotations,
                source.pid,
                com.sun.tools.javac.util.List.from(imports)
        );
        StringWriter buffer = new StringWriter();
        Pretty prettyPrint = new Pretty(buffer, true);

        try {
            prettyPrint.printUnit(after, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return buffer.toString();
    }

    private static int comparePosition(JCTree left, JCTree right) {
        return Integer.compare(left.getStartPosition(), right.getStartPosition());
    }

    private static boolean isImportSection(JCTree tree) {
        switch (tree.getKind()) {
            case IMPORT:
                return true;
            default:
                return false;
        }
    }

    private static boolean importEquals(JCTree.JCImport i, String packageName, String className) {
        JCTree.JCFieldAccess access = (JCTree.JCFieldAccess) i.qualid;

        return access.selected.toString().equals(packageName) && access.name.toString().equals(className);
    }
}