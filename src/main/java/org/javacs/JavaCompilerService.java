package org.javacs;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.*;
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
    // TODO intercept files that aren't in the batch and erase method bodies so compilation is faster
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

    String pathBasedPackageName(Path javaFile) {
        if (!javaFile.getFileName().toString().endsWith(".java")) {
            LOG.warning(javaFile + " does not end in .java");
            return "???";
        }
        for (var dir : sourcePath) {
            if (!javaFile.startsWith(dir)) continue;
            var packageDir = javaFile.getParent();
            var relative = dir.relativize(packageDir);
            return relative.toString().replace('/', '.');
        }
        LOG.warning(javaFile + " is not in the source path " + sourcePath);
        return "???";
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

    public CompileBatch compileBatch(Collection<URI> uris) {
        return compileBatch(uris, ReportProgress.EMPTY);
    }

    public CompileBatch compileBatch(Collection<URI> uris, ReportProgress progress) {
        var files = new ArrayList<File>();
        for (var p : uris) files.add(new File(p));
        var sources = fileManager.getJavaFileObjectsFromFiles(files);
        var list = new ArrayList<JavaFileObject>();
        for (var s : sources) list.add(s);
        return new CompileBatch(this, list, progress);
    }

    public CompileBatch compileBatch(Map<URI, String> sources) {
        return compileBatch(sources, ReportProgress.EMPTY);
    }

    public CompileBatch compileBatch(Map<URI, String> sources, ReportProgress progress) {
        var list = new ArrayList<JavaFileObject>();
        for (var kv : sources.entrySet()) {
            list.add(new StringFileObject(kv.getValue(), kv.getKey()));
        }
        return new CompileBatch(this, list, progress);
    }

    public List<Diagnostic<? extends JavaFileObject>> reportErrors(Collection<URI> uris) {
        LOG.info(String.format("Report errors in %d files...", uris.size()));

        var options = options(sourcePath, classPath);
        // Construct list of sources
        var files = new ArrayList<File>();
        for (var p : uris) files.add(new File(p));
        var sources = fileManager.getJavaFileObjectsFromFiles(files);
        // Create task
        var task =
                (JavacTask) compiler.getTask(null, fileManager, diags::add, options, Collections.emptyList(), sources);
        // Print timing information for optimization
        var profiler = new Profiler();
        task.addTaskListener(profiler);
        // Run compilation
        diags.clear();
        try {
            task.analyze();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        profiler.print();

        LOG.info(String.format("...found %d errors", diags.size()));

        return Collections.unmodifiableList(new ArrayList<>(diags));
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

    public List<URI> potentialDefinitions(Element to) {
        // TODO only methods and types can have multiple definitions
        // TODO reduce number of files we need to check by parsing and eliminating more cases
        return matchesName(to);
    }

    // TODO should probably cache this
    public List<URI> potentialReferences(Element to) {
        // TODO only methods and types can have multiple definitions
        // TODO reduce number of files we need to check by parsing and eliminating more cases
        return matchesName(to);
    }

    private List<URI> matchesName(Element to) {
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
