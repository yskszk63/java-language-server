package org.javacs;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.element.*;
import javax.tools.*;

// TODO eliminate uses of URI in favor of Path
public class JavaCompilerService {
    // Not modifiable! If you want to edit these, you need to create a new instance
    final Set<Path> sourcePath, classPath, docPath;
    final Supplier<Set<Path>> allJavaFiles;
    final JavaCompiler compiler = ServiceLoader.load(JavaCompiler.class).iterator().next();
    final Docs docs;
    final ClassSource jdkClasses = Classes.jdkTopLevelClasses(), classPathClasses;
    // Diagnostics from the last compilation task
    final List<Diagnostic<? extends JavaFileObject>> diags = new ArrayList<>();
    // Use the same file manager for multiple tasks, so we don't repeatedly re-compile the same files
    final StandardJavaFileManager fileManager =
            new FileManagerWrapper(compiler.getStandardFileManager(diags::add, null, Charset.defaultCharset()));

    public JavaCompilerService(
            Set<Path> sourcePath, Supplier<Set<Path>> allJavaFiles, Set<Path> classPath, Set<Path> docPath) {
        System.err.println("Source path:");
        for (var p : sourcePath) {
            System.err.println("  " + p);
        }
        System.err.println("Class path:");
        for (var p : classPath) {
            System.err.println("  " + p);
        }
        System.err.println("Doc path:");
        for (var p : docPath) {
            System.err.println("  " + p);
        }
        // sourcePath and classPath can't actually be modified, because JavaCompiler remembers them from task to task
        this.sourcePath = Collections.unmodifiableSet(sourcePath);
        this.allJavaFiles = allJavaFiles;
        this.classPath = Collections.unmodifiableSet(classPath);
        this.docPath = Collections.unmodifiableSet(docPath);
        var docSourcePath = new HashSet<Path>();
        docSourcePath.addAll(sourcePath);
        docSourcePath.addAll(docPath);
        this.docs = new Docs(docSourcePath);
        this.classPathClasses = Classes.classPathTopLevelClasses(classPath);
    }

    /** Combine source path or class path entries using the system separator, for example ':' in unix */
    private static String joinPath(Collection<Path> classOrSourcePath) {
        return classOrSourcePath.stream().map(p -> p.toString()).collect(Collectors.joining(File.pathSeparator));
    }

    static List<String> options(Set<Path> sourcePath, Set<Path> classPath) {
        var list = new ArrayList<String>();

        Collections.addAll(list, "-classpath", joinPath(classPath));
        Collections.addAll(list, "-sourcepath", joinPath(sourcePath));
        // Collections.addAll(list, "-verbose");
        Collections.addAll(list, "-proc:none");
        Collections.addAll(list, "-g");
        // You would think we could do -Xlint:all,
        // but some lints trigger fatal errors in the presence of parse errors
        Collections.addAll(
                list,
                "-Xlint:cast",
                "-Xlint:deprecation",
                "-Xlint:empty",
                "-Xlint:fallthrough",
                "-Xlint:finally",
                "-Xlint:path",
                "-Xlint:unchecked",
                "-Xlint:varargs",
                "-Xlint:static");

        return list;
    }

    private Collection<Path> removeModuleInfo(Collection<Path> files) {
        var result = new ArrayList<Path>();
        for (var f : files) {
            if (f.getFileName().endsWith("module-info.java")) LOG.info("Skip " + f);
            else result.add(f);
        }
        return result;
    }

    static Iterable<Path> javaSourcesInDir(Path dir) {
        var match = FileSystems.getDefault().getPathMatcher("glob:*.java");

        try {
            // TODO instead of looking at EVERY file, once you see a few files with the same source directory,
            // ignore all subsequent files in the directory
            return Files.walk(dir).filter(java -> match.matches(java.getFileName()))::iterator;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Docs docs() {
        return docs;
    }

    public ParseFile parseFile(URI file, String contents) {
        return new ParseFile(this, file, contents);
    }

    public CompileFocus compileFocus(URI file, String contents, int line, int character) {
        return new CompileFocus(this, file, contents, line, character);
    }

    public CompileFile compileFile(URI file, String contents) {
        return new CompileFile(this, file, contents);
    }

    public CompileBatch compileBatch(Collection<URI> files) {
        return compileBatch(files, ReportProgress.EMPTY);
    }

    public CompileBatch compileBatch(Collection<URI> files, ReportProgress progress) {
        return new CompileBatch(this, files, progress);
    }

    static boolean containsImport(String toPackage, String toClass, Path file) {
        if (toPackage.isEmpty()) return true;
        var samePackage = Pattern.compile("^package +" + toPackage + ";");
        var importClass = Pattern.compile("^import +" + toPackage + "\\." + toClass + ";");
        var importStar = Pattern.compile("^import +" + toPackage + "\\.\\*;");
        var importStatic = Pattern.compile("^import +static +" + toPackage + "\\." + toClass);
        var startOfClass = Pattern.compile("^[\\w ]*class +\\w+");
        try (var read = Files.newBufferedReader(file)) {
            while (true) {
                var line = read.readLine();
                if (line == null) return false;
                if (startOfClass.matcher(line).find()) return false;
                if (samePackage.matcher(line).find()) return true;
                if (importClass.matcher(line).find()) return true;
                if (importStar.matcher(line).find()) return true;
                if (importStatic.matcher(line).find()) return true;
                if (importClass.matcher(line).find()) return true;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean containsWord(String name, Path file) {
        if (!name.matches("\\w*")) throw new RuntimeException(String.format("`%s` is not a word", name));
        return Parser.containsWord(file, name);
    }

    static boolean importsAnyClass(String toPackage, List<String> toClasses, Path file) {
        if (toPackage.isEmpty()) return true; // If package is empty, everyone imports it
        var toClass = toClasses.stream().collect(Collectors.joining("|"));
        var samePackage = Pattern.compile("^package +" + toPackage + ";");
        var importClass = Pattern.compile("^import +" + toPackage + "\\.(" + toClass + ");");
        var importStar = Pattern.compile("^import +" + toPackage + "\\.\\*;");
        var importStatic = Pattern.compile("^import +static +" + toPackage + "\\.");
        var startOfClass = Pattern.compile("^[\\w ]*class +\\w+");
        try (var read = Files.newBufferedReader(file)) {
            while (true) {
                var line = read.readLine();
                if (line == null) break;
                if (startOfClass.matcher(line).find()) break;
                if (samePackage.matcher(line).find()) return true;
                if (importClass.matcher(line).find()) return true;
                if (importStar.matcher(line).find()) return true;
                if (importStatic.matcher(line).find()) return true;
                if (importClass.matcher(line).find()) return true;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    // TODO should probably cache this
    public List<URI> potentialReferences(Element to) {
        LOG.info(String.format("Find potential references to `%s`...", to));

        // Check all files on source path
        var allFiles = allJavaFiles.get();
        LOG.info(String.format("...check %d files on the source path", allFiles.size()));

        // Figure out which files import `to`, explicitly or implicitly
        var toPackage = packageName(to);
        var toClass = className(to);
        var hasImport = new ArrayList<Path>();
        for (var file : allFiles) {
            if (containsImport(toPackage, toClass, file)) {
                hasImport.add(file);
            }
        }
        LOG.info(String.format("...%d files import %s.%s", hasImport.size(), toPackage, toClass));

        // Figure out which of those files have the word `to`
        var name = to.getSimpleName().toString();
        if (name.equals("<init>")) name = to.getEnclosingElement().getSimpleName().toString();
        var hasWord = new ArrayList<URI>();
        for (var file : hasImport) {
            if (containsWord(name, file)) {
                hasWord.add(file.toUri());
            }
        }
        LOG.info(String.format("...%d files contain the word `%s`", hasWord.size(), name));

        return hasWord;
    }

    public static String packageName(Element e) {
        while (e != null) {
            if (e instanceof PackageElement) {
                var pkg = (PackageElement) e;
                return pkg.getQualifiedName().toString();
            }
            e = e.getEnclosingElement();
        }
        return "";
    }

    public static String className(Element e) {
        while (e != null) {
            if (e instanceof TypeElement) {
                var type = (TypeElement) e;
                return type.getSimpleName().toString();
            }
            e = e.getEnclosingElement();
        }
        return "";
    }

    public static String className(TreePath t) {
        while (t != null) {
            if (t.getLeaf() instanceof ClassTree) {
                var cls = (ClassTree) t.getLeaf();
                return cls.getSimpleName().toString();
            }
            t = t.getParentPath();
        }
        return "";
    }

    public static Optional<String> memberName(TreePath t) {
        while (t != null) {
            if (t.getLeaf() instanceof ClassTree) {
                return Optional.empty();
            } else if (t.getLeaf() instanceof MethodTree) {
                var method = (MethodTree) t.getLeaf();
                var name = method.getName().toString();
                return Optional.of(name);
            } else if (t.getLeaf() instanceof VariableTree) {
                var field = (VariableTree) t.getLeaf();
                var name = field.getName().toString();
                return Optional.of(name);
            }
            t = t.getParentPath();
        }
        return Optional.empty();
    }

    // TODO should probably cache this
    private Collection<URI> potentialReferencesToClasses(String toPackage, List<String> toClasses) {
        // Filter for files that import toPackage.toClass
        var result = new LinkedHashSet<URI>();
        for (var dir : sourcePath) {
            // TODO folders can get deleted, need to re-create compiler at that point
            for (var file : javaSourcesInDir(dir)) {
                if (importsAnyClass(toPackage, toClasses, file)) {
                    result.add(file.toUri());
                }
            }
        }
        return result;
    }

    private List<String> allClassNames(CompilationUnitTree root) {
        var result = new ArrayList<String>();
        class FindClasses extends TreeScanner<Void, Void> {
            @Override
            public Void visitClass(ClassTree classTree, Void __) {
                var className = Objects.toString(classTree.getSimpleName(), "");
                result.add(className);
                return null;
            }
        }
        root.accept(new FindClasses(), null);
        return result;
    }

    private Map<URI, Index> index = new HashMap<>();

    private void updateIndex(Collection<URI> possible, ReportProgress progress) {
        LOG.info(String.format("Check %d files for modifications compared to index...", possible.size()));
        var outOfDate = new ArrayList<URI>();
        for (var p : possible) {
            var i = index.getOrDefault(p, Index.EMPTY);
            var modified = Instant.ofEpochMilli(new File(p).lastModified());
            if (modified.isAfter(i.created)) {
                outOfDate.add(p);
            }
        }
        LOG.info(String.format("... %d files are out-of-date", outOfDate.size()));
        // If there's nothing to update, return
        if (outOfDate.isEmpty()) return;
        // If there's more than 1 file, report progress
        if (outOfDate.size() > 1) { // TODO this could probably be tuned to be higher
            progress.start(String.format("Index %d files", outOfDate.size()));
        } else {
            progress = ReportProgress.EMPTY;
        }
        // Reindex
        var counts = compileBatch(outOfDate, progress).countReferences();
        index.putAll(counts);
    }

    public Map<Ptr, Integer> countReferences(URI file, String contents, ReportProgress progress) {
        var root = Parser.parse(new StringFileObject(contents, file));
        // List all files that import file
        var toPackage = Objects.toString(root.getPackageName(), "");
        var toClasses = allClassNames(root);
        var possible = potentialReferencesToClasses(toPackage, toClasses);
        if (possible.isEmpty()) {
            LOG.info("No potential references to " + file);
            return Map.of();
        }
        // Reindex only files that are out-of-date
        updateIndex(possible, progress);
        // Assemble results
        var result = new HashMap<Ptr, Integer>();
        for (var p : possible) {
            var i = index.get(p);
            for (var r : i.refs) {
                var count = result.getOrDefault(r, 0);
                result.put(r, count + 1);
            }
        }
        return result;
    }

    public List<TreePath> findSymbols(String query, int limit) {
        LOG.info(String.format("Searching for `%s`...", query));

        var result = new ArrayList<TreePath>();
        var files = allJavaFiles.get();
        var checked = 0;
        var parsed = 0;
        for (var file : files) {
            checked++;
            // First do a fast check if the query matches anything in a file
            if (!Parser.containsWordMatching(file, query)) continue;
            // Parse the file and check class members for matches
            LOG.info(String.format("...%s contains text matches", file.getFileName()));
            var parse = Parser.parse(file);
            var symbols = Parser.findSymbolsMatching(parse, query);
            parsed++;
            // If we confirm matches, add them to the results
            if (symbols.size() > 0) LOG.info(String.format("...found %d occurrences", symbols.size()));
            result.addAll(symbols);
            // If results are full, stop
            if (result.size() >= limit) break;
        }
        LOG.info(String.format("Found %d matches in %d/%d/%d files", result.size(), checked, parsed, files.size()));

        return result;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
