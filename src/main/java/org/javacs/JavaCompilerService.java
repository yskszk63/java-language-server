package org.javacs;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.tools.*;
import org.javacs.lsp.SymbolInformation;
import org.javacs.rewrite.*;

class JavaCompilerService implements CompilerProvider {
    // Not modifiable! If you want to edit these, you need to create a new instance
    final Set<Path> classPath, docPath;
    final Set<String> addExports;
    final ReusableCompiler compiler = new ReusableCompiler();
    final Docs docs;
    final Set<String> jdkClasses = ScanClassPath.jdkTopLevelClasses(), classPathClasses;
    // Diagnostics from the last compilation task
    final List<Diagnostic<? extends JavaFileObject>> diags = new ArrayList<>();
    // Use the same file manager for multiple tasks, so we don't repeatedly re-compile the same files
    // TODO intercept files that aren't in the batch and erase method bodies so compilation is faster
    final SourceFileManager fileManager;

    JavaCompilerService(Set<Path> classPath, Set<Path> docPath, Set<String> addExports) {
        System.err.println("Class path:");
        for (var p : classPath) {
            System.err.println("  " + p);
        }
        System.err.println("Doc path:");
        for (var p : docPath) {
            System.err.println("  " + p);
        }
        // classPath can't actually be modified, because JavaCompiler remembers it from task to task
        this.classPath = Collections.unmodifiableSet(classPath);
        this.docPath = Collections.unmodifiableSet(docPath);
        this.addExports = Collections.unmodifiableSet(addExports);
        this.docs = new Docs(docPath);
        this.classPathClasses = ScanClassPath.classPathTopLevelClasses(classPath);
        this.fileManager = new SourceFileManager();
    }

    CompileBatch compileBatch(Collection<? extends JavaFileObject> sources) {
        var firstAttempt = new CompileBatch(this, sources);
        var addFiles = firstAttempt.needsAdditionalSources();
        if (addFiles.isEmpty()) return firstAttempt;
        // If the compiler needs additional source files that contain package-private files
        LOG.info("Need to recompile with " + addFiles);
        firstAttempt.close();
        var moreSources = new ArrayList<JavaFileObject>();
        moreSources.addAll(sources);
        for (var add : addFiles) {
            moreSources.add(new SourceFileObject(add));
        }
        return new CompileBatch(this, moreSources);
    }

    List<SymbolInformation> findSymbols(String query, int limit) {
        LOG.info(String.format("Searching for `%s`...", query));
        var result = new ArrayList<SymbolInformation>();
        var files = FileStore.all();
        var checked = 0;
        var parsed = 0;
        for (var file : files) {
            checked++;
            // First do a fast check if the query matches anything in a file
            if (!StringSearch.containsWordMatching(file, query)) continue;
            // Parse the file and check class members for matches
            LOG.info(String.format("...%s contains text matches", file.getFileName()));
            var parse = Parser.parseFile(file);
            var symbols = parse.findSymbolsMatching(query);
            parsed++;
            // If we confirm matches, add them to the results
            if (symbols.size() > 0) {
                LOG.info(String.format("...found %d occurrences", symbols.size()));
            }
            result.addAll(symbols);
            // If results are full, stop
            if (result.size() >= limit) break;
        }
        LOG.info(String.format("Found %d matches in %d/%d/%d files", result.size(), checked, parsed, files.size()));

        return result;
    }

    private static final Pattern PACKAGE_EXTRACTOR = Pattern.compile("^([a-z][_a-zA-Z0-9]*\\.)*[a-z][_a-zA-Z0-9]*");

    private String packageName(String className) {
        var m = PACKAGE_EXTRACTOR.matcher(className);
        if (m.find()) {
            return m.group();
        }
        return "";
    }

    private static final Pattern CLASS_EXTRACTOR = Pattern.compile("[A-Z][_a-zA-Z0-9]*$");

    private String className(String className) {
        var m = CLASS_EXTRACTOR.matcher(className);
        if (m.find()) {
            return m.group();
        }
        return "";
    }

    private static final Cache<String, Boolean> cacheContainsWord = new Cache<>();

    private boolean containsWord(Path file, String word) {
        if (cacheContainsWord.needs(file, word)) {
            cacheContainsWord.load(file, word, StringSearch.containsWord(file, word));
        }
        return cacheContainsWord.get(file, word);
    }

    private static final Cache<Void, List<String>> cacheContainsType = new Cache<>();

    private boolean containsType(Path file, String simpleName) {
        if (cacheContainsType.needs(file, null)) {
            var root = parse(file).root;
            var types = new ArrayList<String>();
            new FindTypeDeclarations().scan(root, types);
            cacheContainsType.load(file, null, types);
        }
        return cacheContainsType.get(file, null).contains(simpleName);
    }

    private static final Cache<Void, List<String>> cacheContainsImport = new Cache<>();

    private boolean containsImport(Path file, String className) {
        if (cacheContainsImport.needs(file, null)) {
            var root = parse(file).root;
            var types = new ArrayList<String>();
            new FindImports().scan(root, types);
            cacheContainsImport.load(file, null, types);
        }
        var star = packageName(className) + ".*";
        for (var i : cacheContainsImport.get(file, null)) {
            if (i.equals(className) || i.equals(star)) return true;
        }
        return false;
    }

    @Override
    public Path findTypeDeclaration(String className) {
        var packageName = packageName(className);
        var simpleName = className(className);
        for (var f : FileStore.list(packageName)) {
            if (containsWord(f, simpleName) && containsType(f, simpleName)) {
                return f;
            }
        }
        return NOT_FOUND;
    }

    @Override
    public Path[] findTypeReferences(String className) {
        var packageName = packageName(className);
        var candidates = new ArrayList<Path>();
        for (var f : FileStore.all()) {
            if (containsWord(f, packageName) && containsImport(f, className)) {
                candidates.add(f);
            }
        }
        return candidates.toArray(new Path[candidates.size()]);
    }

    @Override
    public Path[] findMemberReferences(String className, String memberName) {
        var packageName = packageName(className);
        var candidates = new ArrayList<Path>();
        for (var f : FileStore.all()) {
            if (containsWord(f, packageName) && containsImport(f, className) && containsWord(f, memberName)) {
                candidates.add(f);
            }
        }
        return candidates.toArray(new Path[candidates.size()]);
    }

    @Override
    public ParseTask parse(Path file) {
        var parser = Parser.parseFile(file);
        return new ParseTask(parser.task, parser.root);
    }

    @Override
    public CompileTask compile(Path... files) {
        var sources = new ArrayList<JavaFileObject>();
        for (var f : files) {
            sources.add(new SourceFileObject(f));
        }
        var compile = compileBatch(sources);
        return new CompileTask(compile.task, compile.roots, compile::close);
    }

    private static final Logger LOG = Logger.getLogger("main");
}
