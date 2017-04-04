package org.javacs;

import com.google.common.collect.Lists;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
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
import java.util.*;

class RefactorFile {
    private final JavacTask task;
    private final CompilationUnitTree source;
    private final SourcePositions pos;
    private static final Position zero = new Position(0, 0);

    RefactorFile(JavacTask task, CompilationUnitTree source) {
        this.task = task;
        this.source = source;
        this.pos = Trees.instance(task).getSourcePositions();
    }

    List<TextEdit> addImport(String packageName, String className) {
        Objects.requireNonNull(packageName, "Package name is null");
        Objects.requireNonNull(className, "Class name is null");

        if (alreadyImported(source, packageName, className))
            return Collections.emptyList();

        Position lastExistingImport = endOfImports();
        String afterString = organizeImports(source, packageName, className);

        if (lastExistingImport.equals(zero))
            afterString += "\n";

        return Collections.singletonList(new TextEdit(new Range(zero, lastExistingImport), afterString));
    }

    private Position endOfImports() {
        Position endOfPackage = Optional.ofNullable(source.getPackageName())
                .map(p -> findEndOfLine(source.getSourceFile(), pos.getStartPosition(source, p)))
                .orElse(zero);
        return source.getImports().stream()
                .max(this::comparePosition)
                .map(def -> pos.getStartPosition(source, def))
                .map(offset -> findEndOfLine(source.getSourceFile(), offset))
                .orElse(endOfPackage);
    }

    /**
     * Convert on offset-based position to a {@link Position}
     */
    static Position findEndOfLine(JavaFileObject file, long findOffset) {
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

    private static boolean alreadyImported(CompilationUnitTree source, String packageName, String className) {
        return source.getImports()
                    .stream()
                    .anyMatch(i -> importEquals(i, packageName, className));
    }

    private static String organizeImports(CompilationUnitTree source, String packageName, String className) {
        Context context = new Context();
        JavacFileManager fileManager = new JavacFileManager(context, true, null);
        Names names = Names.instance(context);
        TreeMaker factory = TreeMaker.instance(context);
        List<ImportTree> imports = Lists.newArrayList(source.getImports());

        imports.add(factory.Import(
                factory.Select(
                        factory.Ident(names.fromString(packageName)),
                        names.fromString(className)),
                false
        ));

        JCTree.JCCompilationUnit after = factory.TopLevel(
                list(JCTree.JCAnnotation.class, source.getPackageAnnotations()),
                (JCTree.JCExpression) source.getPackageName(),
                list(JCTree.class, imports)
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

    private static <T> com.sun.tools.javac.util.List<T> list(Class<T> cast, List<?> from) {
        List<T> out = new ArrayList<>();

        for (Object each : from) {
            out.add(cast.cast(each));
        }

        return com.sun.tools.javac.util.List.from(out);
    }

    private int comparePosition(Tree left, Tree right) {
        long leftPos = pos.getStartPosition(source, left);
        long rightPos = pos.getStartPosition(source, right);

        return Long.compare(leftPos, rightPos);
    }

    private static boolean importEquals(ImportTree i, String packageName, String className) {
        MemberSelectTree access = (MemberSelectTree) i.getQualifiedIdentifier();

        return access.getExpression().toString().equals(packageName) && access.getIdentifier().toString().equals(className);
    }
}