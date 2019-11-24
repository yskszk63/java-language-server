package org.javacs;

import com.google.gson.*;
import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;
import org.javacs.lsp.*;
import org.javacs.rewrite.*;

class JavaLanguageServer extends LanguageServer {
    // TODO allow multiple workspace roots
    private Path workspaceRoot;
    private final LanguageClient client;
    private JavaCompilerService cacheCompiler;
    private JsonObject cacheSettings;
    private JsonObject settings = new JsonObject();
    private boolean modifiedBuild = true;

    JavaCompilerService compiler() {
        if (needsCompiler()) {
            cacheCompiler = createCompiler();
            cacheSettings = settings;
            modifiedBuild = false;
        }
        return cacheCompiler;
    }

    private boolean needsCompiler() {
        if (modifiedBuild) {
            return true;
        }
        if (!settings.equals(cacheSettings)) {
            LOG.info("Settings\n\t" + settings + "\nis different than\n\t" + cacheSettings);
            return true;
        }
        return false;
    }

    void lint(Collection<Path> files) {
        if (files.isEmpty()) {
            return;
        }
        LOG.info("Lint " + files.size() + " files...");
        var started = Instant.now();
        var sources = asSourceFiles(files);
        try (var batch = compiler().compileBatch(sources)) {
            LOG.info(String.format("...compiled in %d ms", elapsed(started)));
            publishDiagnostics(files, batch);
        }
        LOG.info(String.format("...linted in %d ms", elapsed(started)));
    }

    private List<SourceFileObject> asSourceFiles(Collection<Path> files) {
        var sources = new ArrayList<SourceFileObject>();
        for (var f : files) {
            sources.add(new SourceFileObject(f, FileStore.contents(f), FileStore.modified(f)));
        }
        return sources;
    }

    private void publishDiagnostics(Collection<Path> files, CompileBatch batch) {
        for (var f : files) {
            var errors = batch.reportErrors(f);
            var colors = batch.colors(f);
            client.publishDiagnostics(new PublishDiagnosticsParams(f.toUri(), errors));
            client.customNotification("java/colors", GSON.toJsonTree(colors));
        }
    }

    private long elapsed(Instant since) {
        return Duration.between(since, Instant.now()).toMillis();
    }

    static final Gson GSON = new GsonBuilder().registerTypeAdapter(Ptr.class, new PtrAdapter()).create();

    private void javaStartProgress(JavaStartProgressParams params) {
        client.customNotification("java/startProgress", GSON.toJsonTree(params));
    }

    private void javaReportProgress(JavaReportProgressParams params) {
        client.customNotification("java/reportProgress", GSON.toJsonTree(params));
    }

    private void javaEndProgress() {
        client.customNotification("java/endProgress", JsonNull.INSTANCE);
    }

    private JavaCompilerService createCompiler() {
        Objects.requireNonNull(workspaceRoot, "Can't create compiler because workspaceRoot has not been initialized");

        javaStartProgress(new JavaStartProgressParams("Configure javac"));
        javaReportProgress(new JavaReportProgressParams("Finding source roots"));

        var externalDependencies = externalDependencies();
        var classPath = classPath();
        var addExports = addExports();
        // If classpath is specified by the user, don't infer anything
        if (!classPath.isEmpty()) {
            javaEndProgress();
            return new JavaCompilerService(classPath, Collections.emptySet(), addExports);
        }
        // Otherwise, combine inference with user-specified external dependencies
        else {
            var infer = new InferConfig(workspaceRoot, externalDependencies);

            javaReportProgress(new JavaReportProgressParams("Inferring class path"));
            classPath = infer.classPath();

            javaReportProgress(new JavaReportProgressParams("Inferring doc path"));
            var docPath = infer.buildDocPath();

            javaEndProgress();
            return new JavaCompilerService(classPath, docPath, addExports);
        }
    }

    private Set<String> externalDependencies() {
        if (!settings.has("externalDependencies")) return Set.of();
        var array = settings.getAsJsonArray("externalDependencies");
        var strings = new HashSet<String>();
        for (var each : array) {
            strings.add(each.getAsString());
        }
        return strings;
    }

    private Set<Path> classPath() {
        if (!settings.has("classPath")) return Set.of();
        var array = settings.getAsJsonArray("classPath");
        var paths = new HashSet<Path>();
        for (var each : array) {
            paths.add(Paths.get(each.getAsString()).toAbsolutePath());
        }
        return paths;
    }

    private Set<String> addExports() {
        if (!settings.has("addExports")) return Set.of();
        var array = settings.getAsJsonArray("addExports");
        var strings = new HashSet<String>();
        for (var each : array) {
            strings.add(each.getAsString());
        }
        return strings;
    }

    @Override
    public InitializeResult initialize(InitializeParams params) {
        this.workspaceRoot = Paths.get(params.rootUri);
        FileStore.setWorkspaceRoots(Set.of(Paths.get(params.rootUri)));

        var c = new JsonObject();
        c.addProperty("textDocumentSync", 2); // Incremental
        c.addProperty("hoverProvider", true);
        var completionOptions = new JsonObject();
        completionOptions.addProperty("resolveProvider", true);
        var triggerCharacters = new JsonArray();
        triggerCharacters.add(".");
        completionOptions.add("triggerCharacters", triggerCharacters);
        c.add("completionProvider", completionOptions);
        var signatureHelpOptions = new JsonObject();
        var signatureTrigger = new JsonArray();
        signatureTrigger.add("(");
        signatureTrigger.add(",");
        signatureHelpOptions.add("triggerCharacters", signatureTrigger);
        c.add("signatureHelpProvider", signatureHelpOptions);
        c.addProperty("referencesProvider", true);
        c.addProperty("definitionProvider", true);
        c.addProperty("workspaceSymbolProvider", true);
        c.addProperty("documentSymbolProvider", true);
        c.addProperty("documentFormattingProvider", true);
        var codeLensOptions = new JsonObject();
        c.add("codeLensProvider", codeLensOptions);
        c.addProperty("foldingRangeProvider", true);
        c.addProperty("codeActionProvider", true);
        var renameOptions = new JsonObject();
        renameOptions.addProperty("prepareProvider", true);
        c.add("renameProvider", renameOptions);

        return new InitializeResult(c);
    }

    private static final String[] watchFiles = {
        "**/*.java", "**/pom.xml", "**/BUILD",
    };

    @Override
    public void initialized() {
        client.registerCapability("workspace/didChangeWatchedFiles", watchFiles(watchFiles));
    }

    private JsonObject watchFiles(String... globPatterns) {
        var options = new JsonObject();
        var watchers = new JsonArray();
        for (var p : globPatterns) {
            var config = new JsonObject();
            config.addProperty("globPattern", p);
            watchers.add(config);
        }
        options.add("watchers", watchers);
        return options;
    }

    @Override
    public void shutdown() {}

    public JavaLanguageServer(LanguageClient client) {
        this.client = client;
    }

    @Override
    public List<SymbolInformation> workspaceSymbols(WorkspaceSymbolParams params) {
        return compiler().findSymbols(params.query, 50);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams change) {
        var java = change.settings.getAsJsonObject().get("java");
        LOG.info("Received java settings " + java);
        settings = java.getAsJsonObject();
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        for (var c : params.changes) {
            var file = Paths.get(c.uri);
            if (FileStore.isJavaFile(file)) {
                switch (c.type) {
                    case FileChangeType.Created:
                        FileStore.externalCreate(file);
                        break;
                    case FileChangeType.Changed:
                        FileStore.externalChange(file);
                        break;
                    case FileChangeType.Deleted:
                        FileStore.externalDelete(file);
                        break;
                }
                return;
            }
            var name = file.getFileName().toString();
            switch (name) {
                case "BUILD":
                case "pom.xml":
                    LOG.info("Compiler needs to be re-created because " + file + "has changed");
                    modifiedBuild = true;
            }
        }
    }

    static int isMemberSelect(String contents, int cursor) {
        // Start at char before cursor
        cursor--;
        // Move back until we find a non-identifier char
        while (cursor > 0 && Character.isJavaIdentifierPart(contents.charAt(cursor))) {
            cursor--;
        }
        if (cursor <= 0 || contents.charAt(cursor) != '.') {
            return -1;
        }
        // Move cursor back until we find a non-whitespace char
        while (cursor > 0 && Character.isWhitespace(contents.charAt(cursor - 1))) {
            cursor--;
        }
        return cursor;
    }

    static int isMemberReference(String contents, int cursor) {
        // Start at char before cursor
        cursor--;
        // Move back until we find a non-identifier char
        while (cursor > 1 && Character.isJavaIdentifierPart(contents.charAt(cursor))) {
            cursor--;
        }
        if (!contents.startsWith("::", cursor - 1)) {
            return -1;
        }
        // Skip first : in ::
        cursor--;
        // Move cursor back until we find a non-whitespace char
        while (cursor > 0 && Character.isWhitespace(contents.charAt(cursor - 1))) {
            cursor--;
        }
        return cursor;
    }

    private static boolean isQualifiedIdentifierPart(char c) {
        return Character.isJavaIdentifierPart(c) || c == '.';
    }

    static int isPartialAnnotation(String contents, int cursor) {
        // Start at char before cursor
        cursor--;
        // Move back until we find a non-identifier char
        while (cursor > 0 && isQualifiedIdentifierPart(contents.charAt(cursor))) {
            cursor--;
        }
        if (cursor >= 0 && contents.charAt(cursor) == '@') {
            return cursor;
        } else {
            return -1;
        }
    }

    static boolean isPartialCase(String contents, int cursor) {
        // Start at char before cursor
        cursor--;
        // Move back until we find a non-identifier char
        while (cursor > 0 && Character.isJavaIdentifierPart(contents.charAt(cursor))) {
            cursor--;
        }
        // Skip space
        while (cursor > 0 && Character.isWhitespace(contents.charAt(cursor))) {
            cursor--;
        }
        return contents.startsWith("case", cursor - 3);
    }

    static String partialName(String contents, int cursor) {
        // Start at char before cursor
        var start = cursor - 1;
        // Move back until we find a non-identifier char
        while (start >= 0 && Character.isJavaIdentifierPart(contents.charAt(start))) {
            start--;
        }
        return contents.substring(start + 1, cursor);
    }

    private static String restOfLine(String contents, int cursor) {
        var endOfLine = contents.indexOf('\n', cursor);
        if (endOfLine == -1) {
            return contents.substring(cursor);
        }
        return contents.substring(cursor, endOfLine);
    }

    private static boolean hasParen(String contents, int cursor) {
        return cursor < contents.length() && contents.charAt(cursor) == '(';
    }

    @Override
    public Optional<CompletionList> completion(TextDocumentPositionParams position) {
        var started = Instant.now();
        var uri = position.textDocument.uri;
        if (!FileStore.isJavaFile(uri)) return Optional.empty();
        var file = Paths.get(uri);
        var line = position.position.line + 1;
        var column = position.position.character + 1;
        LOG.info(String.format("Complete at %s(%d,%d)...", file, line, column));
        // Figure out what kind of completion we want to do
        var contents = FileStore.contents(file);
        var cursor = FileStore.offset(contents, line, column);
        var addParens = !hasParen(contents, cursor);
        var addSemi = restOfLine(contents, cursor).matches("\\s*");
        // Complete object. or object.partial
        var dot = isMemberSelect(contents, cursor);
        if (dot != -1) {
            LOG.info("...complete members");
            // Erase .partial
            // contents = eraseRegion(contents, dot, cursor);
            var parse = Parser.parseJavaFileObject(new SourceFileObject(file, contents, Instant.now()));
            contents = parse.prune(dot);
            try (var compile = compiler().compileBatch(List.of(new SourceFileObject(file, contents, Instant.now())))) {
                var list = compile.completeMembers(file, dot, addParens, addSemi);
                logCompletionTiming(started, list, false);
                return Optional.of(new CompletionList(false, list));
            }
        }
        // Complete object:: or object::partial
        var ref = isMemberReference(contents, cursor);
        if (ref != -1) {
            LOG.info("...complete references");
            // Erase ::partial
            // contents = eraseRegion(contents, ref, cursor);
            var parse = Parser.parseJavaFileObject(new SourceFileObject(file, contents, Instant.now()));
            contents = parse.prune(ref);
            try (var compile = compiler().compileBatch(List.of(new SourceFileObject(file, contents, Instant.now())))) {
                var list = compile.completeReferences(file, ref);
                logCompletionTiming(started, list, false);
                return Optional.of(new CompletionList(false, list));
            }
        }
        // Complete @Partial
        var at = isPartialAnnotation(contents, cursor);
        if (at != -1) {
            LOG.info("...complete annotations");
            var partialName = contents.substring(at + 1, cursor);
            var parse = Parser.parseJavaFileObject(new SourceFileObject(file, contents, Instant.now()));
            contents = parse.prune(cursor);
            try (var compile = compiler().compileBatch(List.of(new SourceFileObject(file, contents, Instant.now())))) {
                var list = compile.completeAnnotations(file, cursor, partialName);
                var isIncomplete = list.size() >= CompileBatch.MAX_COMPLETION_ITEMS;
                logCompletionTiming(started, list, isIncomplete);
                return Optional.of(new CompletionList(isIncomplete, list));
            }
        }
        // Complete case partial
        if (isPartialCase(contents, cursor)) {
            LOG.info("...complete members");
            var parse = Parser.parseJavaFileObject(new SourceFileObject(file, contents, Instant.now()));
            contents = parse.eraseCase(cursor);
            parse = Parser.parseJavaFileObject(new SourceFileObject(file, contents, Instant.now()));
            contents = parse.prune(cursor);
            try (var compile = compiler().compileBatch(List.of(new SourceFileObject(file, contents, Instant.now())))) {
                var list = compile.completeCases(file, cursor);
                logCompletionTiming(started, list, false);
                return Optional.of(new CompletionList(false, list));
            }
        }
        // Complete partial
        var looksLikeIdentifier = Character.isJavaIdentifierPart(contents.charAt(cursor - 1));
        if (looksLikeIdentifier) {
            var parse = Parser.parseJavaFileObject(new SourceFileObject(file, contents, Instant.now()));
            if (parse.isIdentifier(cursor)) {
                LOG.info("...complete identifiers");
                contents = parse.prune(cursor);
                parse = Parser.parseJavaFileObject(new SourceFileObject(file, contents, Instant.now()));
                var path = parse.findPath(cursor);
                try (var compile =
                        compiler().compileBatch(List.of(new SourceFileObject(file, contents, Instant.now())))) {
                    var list =
                            compile.completeIdentifiers(
                                    file,
                                    cursor,
                                    Parser.inClass(path),
                                    Parser.inMethod(path),
                                    partialName(contents, cursor),
                                    addParens,
                                    addSemi);
                    var isIncomplete = list.size() >= CompileBatch.MAX_COMPLETION_ITEMS;
                    logCompletionTiming(started, list, isIncomplete);
                    return Optional.of(new CompletionList(isIncomplete, list));
                }
            }
        }
        LOG.info("...complete keywords");
        var items = new ArrayList<CompletionItem>();
        for (var name : CompileBatch.TOP_LEVEL_KEYWORDS) {
            var i = new CompletionItem();
            i.label = name;
            i.kind = CompletionItemKind.Keyword;
            i.detail = "keyword";
            items.add(i);
        }
        return Optional.of(new CompletionList(true, items));
    }

    private void logCompletionTiming(Instant started, List<?> list, boolean isIncomplete) {
        var elapsedMs = Duration.between(started, Instant.now()).toMillis();
        if (isIncomplete) LOG.info(String.format("Found %d items (incomplete) in %,d ms", list.size(), elapsedMs));
        else LOG.info(String.format("...found %d items in %,d ms", list.size(), elapsedMs));
    }

    private Optional<MarkupContent> findDocs(Ptr ptr) {
        LOG.info(String.format("Find docs for `%s`...", ptr));

        // Find el in the doc path
        var file = compiler().docs.find(ptr);
        if (!file.isPresent()) return Optional.empty();
        // Parse file and find el
        var parse = Parser.parseJavaFileObject(file.get());
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return Optional.empty();
        // Parse the doctree associated with el
        var docTree = parse.doc(path.get());
        var string = Parser.asMarkupContent(docTree);
        return Optional.of(string);
    }

    private Optional<String> findMethodDetails(Ptr ptr) {
        LOG.info(String.format("Find details for method `%s`...", ptr));

        // TODO find and parse happens twice
        // Find method in the doc path
        var file = compiler().docs.find(ptr);
        if (!file.isPresent()) return Optional.empty();
        // Parse file and find method
        var parse = Parser.parseJavaFileObject(file.get());
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return Optional.empty();
        // Should be a MethodTree
        var tree = path.get().getLeaf();
        if (!(tree instanceof MethodTree)) {
            LOG.warning(String.format("...method `%s` associated with non-method tree `%s`", ptr, tree));
            return Optional.empty();
        }
        // Write description of method using info from source
        var methodTree = (MethodTree) tree;
        var args = new StringJoiner(", ");
        for (var p : methodTree.getParameters()) {
            args.add(p.getType() + " " + p.getName());
        }
        var details = String.format("%s %s(%s)", methodTree.getReturnType(), methodTree.getName(), args);
        return Optional.of(details);
    }

    @Override
    public CompletionItem resolveCompletionItem(CompletionItem unresolved) {
        if (unresolved.data == null) return unresolved;
        var data = GSON.fromJson(unresolved.data, CompletionData.class);
        var markdown = findDocs(data.ptr);
        if (markdown.isPresent()) {
            unresolved.documentation = markdown.get();
        }
        if (data.ptr.isMethod()) {
            var details = findMethodDetails(data.ptr);
            if (details.isPresent()) {
                unresolved.detail = details.get();
                if (data.plusOverloads != 0) {
                    unresolved.detail += " (+" + data.plusOverloads + " overloads)";
                }
            }
        }
        return unresolved;
    }

    private String hoverTypeDeclaration(TypeElement t) {
        var result = new StringBuilder();
        switch (t.getKind()) {
            case ANNOTATION_TYPE:
                result.append("@interface");
                break;
            case INTERFACE:
                result.append("interface");
                break;
            case CLASS:
                result.append("class");
                break;
            case ENUM:
                result.append("enum");
                break;
            default:
                LOG.warning("Don't know what to call type element " + t);
                result.append("???");
        }
        result.append(" ").append(ShortTypePrinter.DEFAULT.print(t.asType()));
        var superType = ShortTypePrinter.DEFAULT.print(t.getSuperclass());
        switch (superType) {
            case "Object":
            case "none":
                break;
            default:
                result.append(" extends ").append(superType);
        }
        return result.toString();
    }

    private String hoverCode(Element e) {
        if (e instanceof ExecutableElement) {
            var m = (ExecutableElement) e;
            return ShortTypePrinter.DEFAULT.printMethod(m);
        } else if (e instanceof VariableElement) {
            var v = (VariableElement) e;
            return ShortTypePrinter.DEFAULT.print(v.asType()) + " " + v;
        } else if (e instanceof TypeElement) {
            var t = (TypeElement) e;
            var lines = new StringJoiner("\n");
            lines.add(hoverTypeDeclaration(t) + " {");
            for (var member : t.getEnclosedElements()) {
                // TODO check accessibility
                if (member instanceof ExecutableElement || member instanceof VariableElement) {
                    lines.add("  " + hoverCode(member) + ";");
                } else if (member instanceof TypeElement) {
                    lines.add("  " + hoverTypeDeclaration((TypeElement) member) + " { /* removed */ }");
                }
            }
            lines.add("}");
            return lines.toString();
        } else {
            return e.toString();
        }
    }

    private String hoverDocs(Element e) {
        var ptr = new Ptr(e);
        var file = compiler().docs.find(ptr);
        if (!file.isPresent()) return "";
        var parse = Parser.parseJavaFileObject(file.get());
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return "";
        var doc = parse.doc(path.get());
        var md = Parser.asMarkdown(doc);
        return md;
    }

    @Override
    public Optional<Hover> hover(TextDocumentPositionParams position) {
        var uri = position.textDocument.uri;
        var line = position.position.line + 1;
        var column = position.position.character + 1;
        if (!FileStore.isJavaFile(uri)) return Optional.empty();
        var file = Paths.get(uri);
        // Log start time
        LOG.info(String.format("Hover over %s(%d,%d) ...", uri.getPath(), line, column));
        var started = Instant.now();
        // Compile entire file
        var sources = Set.of(new SourceFileObject(file));
        try (var compile = compiler().compileBatch(sources)) {
            // Find element under cursor
            var el = compile.element(compile.tree(file, line, column));
            if (!el.isPresent()) {
                LOG.info("...no element under cursor");
                return Optional.empty();
            }
            // Result is combination of docs and code
            var result = new ArrayList<MarkedString>();
            // Add docs hover message
            var docs = hoverDocs(el.get());
            if (!docs.isBlank()) {
                result.add(new MarkedString(docs));
            }

            // Add code hover message
            var code = hoverCode(el.get());
            result.add(new MarkedString("java", code));
            // Log duration
            var elapsed = Duration.between(started, Instant.now());
            LOG.info(String.format("...found hover in %d ms", elapsed.toMillis()));

            return Optional.of(new Hover(result));
        }
    }

    @Override
    public Optional<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        var uri = position.textDocument.uri;
        if (!FileStore.isJavaFile(uri)) return Optional.empty();
        var file = Paths.get(uri);
        var line = position.position.line + 1;
        var column = position.position.character + 1;
        LOG.info(String.format("Find signature at at %s(%d,%d)...", file, line, column));
        var contents = FileStore.contents(file);
        var cursor = FileStore.offset(contents, line, column);
        var parse = Parser.parseJavaFileObject(new SourceFileObject(file, contents, Instant.now()));
        contents = parse.prune(cursor);
        try (var compile = compiler().compileBatch(List.of(new SourceFileObject(file, contents, Instant.now())))) {
            return compile.signatureHelp(file, cursor);
        }
    }

    @Override
    public Optional<List<Location>> gotoDefinition(TextDocumentPositionParams position) {
        var fromUri = position.textDocument.uri;
        if (!FileStore.isJavaFile(fromUri)) return Optional.empty();
        var fromFile = Paths.get(fromUri);
        var fromLine = position.position.line + 1;
        var fromColumn = position.position.character + 1;

        // Compile from-file and identify element under cursor
        LOG.info(String.format("Go-to-def at %s:%d...", fromUri, fromLine));
        var sources = Set.of(new SourceFileObject(fromFile));
        try (var batch = compiler().compileBatch(sources)) {
            var fromTree = batch.tree(fromFile, fromLine, fromColumn);
            var toEl = batch.element(fromTree);
            if (!toEl.isPresent()) {
                LOG.info(String.format("...no element at cursor"));
                return Optional.empty();
            }
            if (toEl.get().asType().getKind() == TypeKind.ERROR) {
                return gotoErrorDefinition(batch, toEl.get());
            }
            var toFile = findElement(toEl.get());
            if (toFile.isEmpty()) {
                LOG.info(String.format("...no file for %s", toEl.get()));
                return Optional.empty();
            }
            batch.close();
            return resolveGotoDefinition(fromFile, fromLine, fromColumn, toFile.get());
        }
    }

    private Optional<JavaFileObject> findElement(Element toEl) {
        var fromSourcePath = Parser.declaringFile(toEl);
        if (fromSourcePath.isPresent()) {
            return Optional.of(new SourceFileObject(fromSourcePath.get()));
        }
        var fromDocPath = compiler().docs.find(new Ptr(toEl));
        if (fromDocPath.isPresent()) {
            return fromDocPath;
        }
        return Optional.empty();
    }

    private Optional<List<Location>> resolveGotoDefinition(
            Path fromFile, int fromLine, int fromColumn, JavaFileObject toFile) {
        var sources = new HashSet<JavaFileObject>();
        sources.add(new SourceFileObject(fromFile));
        sources.add(toFile);
        try (var batch = compiler().compileBatch(sources)) {
            var fromTree = batch.tree(fromFile, fromLine, fromColumn);
            var toEl = batch.element(fromTree).get();
            var toPath = batch.trees.getPath(toEl);
            if (toPath == null) {
                LOG.info(String.format("...no location for element %s", toEl));
                return Optional.empty();
            }
            var location = batch.location(toPath);
            if (location == Location.NONE) {
                LOG.info(String.format("...no location for tree %s", toPath.getLeaf()));
                return Optional.empty();
            }
            return Optional.of(List.of(location));
        }
    }

    private Optional<List<Location>> gotoErrorDefinition(CompileBatch batch, Element toEl) {
        var name = toEl.getSimpleName();
        if (name == null) {
            LOG.info(String.format("...%s has no name", toEl));
            return Optional.empty();
        }
        var parent = toEl.getEnclosingElement();
        if (!(parent instanceof TypeElement)) {
            LOG.info(String.format("...%s is not a type", parent));
            return Optional.empty();
        }

        var type = (TypeElement) parent;
        var toFile = Parser.declaringFile(type);
        if (toFile.isEmpty()) {
            LOG.info(String.format("...no file for %s", type));
            return Optional.empty();
        }
        batch.close();
        return gotoAllMembers(type.getQualifiedName().toString(), name.toString(), toFile.get());
    }

    private Optional<List<Location>> gotoAllMembers(String typeName, String memberName, Path inFile) {
        LOG.info(String.format("...go to members of %s named %s", typeName, memberName));
        try (var batch = compiler().compileBatch(List.of(new SourceFileObject(inFile)))) {
            var type = batch.elements.getTypeElement(typeName);
            if (type == null) {
                LOG.info(String.format("...no type named %s in %s", typeName, inFile.getFileName()));
                return Optional.empty();
            }
            var matches = new ArrayList<Location>();
            for (var member : batch.elements.getAllMembers(type)) {
                if (!member.getSimpleName().contentEquals(memberName)) continue;
                var path = batch.trees.getPath(member);
                if (path == null) {
                    LOG.info(String.format("...no path for %s in %s", member, inFile.getFileName()));
                    continue;
                }
                var location = batch.location(path);
                if (location == Location.NONE) {
                    LOG.info(String.format("...no location for %s in %s", path.getLeaf(), inFile.getFileName()));
                    continue;
                }
                matches.add(location);
            }
            return Optional.of(matches);
        }
    }

    @Override
    public Optional<List<Location>> findReferences(ReferenceParams position) {
        var toUri = position.textDocument.uri;
        if (!FileStore.isJavaFile(toUri)) return Optional.empty();
        var toFile = Paths.get(toUri);
        var toLine = position.position.line + 1;
        var toColumn = position.position.character + 1;

        // TODO use parser to figure out batch to compile, avoiding compiling twice

        // Compile from-file and identify element under cursor
        LOG.warning(String.format("Looking for references to %s(%d,%d)...", toUri.getPath(), toLine, toColumn));
        Element toEl;
        var sources = Set.of(new SourceFileObject(toFile));
        try (var batch = compiler().compileBatch(sources)) {
            var maybe = batch.element(batch.tree(toFile, toLine, toColumn));
            if (!maybe.isPresent()) {
                LOG.warning("...no element under cursor");
                return Optional.empty();
            }
            toEl = maybe.get();
        }

        // Compile all files that *might* contain references to toEl
        var name = Parser.simpleName(toEl);
        var fromFiles = new HashSet<Path>();
        var isLocal = toEl instanceof VariableElement && !(toEl.getEnclosingElement() instanceof TypeElement);
        if (!isLocal) {
            var isType = false;
            switch (toEl.getKind()) {
                case ANNOTATION_TYPE:
                case CLASS:
                case INTERFACE:
                    isType = true;
            }
            var flags = toEl.getModifiers();
            var possible = Parser.potentialReferences(toFile, name, isType, flags);
            fromFiles.addAll(possible);
        }
        fromFiles.add(toFile);
        var eraseCode = pruneWord(fromFiles, name);
        try (var batch = compiler().compileBatch(eraseCode)) {
            var fromTreePaths = batch.references(toFile, toLine, toColumn);
            LOG.info(String.format("...found %d references", fromTreePaths.size()));
            if (fromTreePaths == CompileBatch.CODE_NOT_FOUND) return Optional.empty();
            var result = new ArrayList<Location>();
            for (var path : fromTreePaths) {
                var fromUri = path.getCompilationUnit().getSourceFile().toUri();
                var fromRange = batch.range(path);
                if (fromRange == Range.NONE) {
                    LOG.warning(String.format("...couldn't locate `%s`", path.getLeaf()));
                    continue;
                }
                var from = new Location(fromUri, fromRange);
                result.add(from);
            }
            return Optional.of(result);
        }
    }

    private List<JavaFileObject> pruneWord(Collection<Path> files, String name) {
        LOG.info(String.format("...prune code that doesn't contain `%s`", name));
        var sources = new ArrayList<JavaFileObject>();
        for (var f : files) {
            var pruned = Parser.parseFile(f).prune(name);
            sources.add(new SourceFileObject(f, pruned, Instant.EPOCH));
        }
        return sources;
    }

    private Parser cacheParse;
    private Path cacheParseFile = Paths.get("/NONE");
    private int cacheParseVersion = -1;

    private void updateCachedParse(Path file) {
        if (file.equals(cacheParseFile) && FileStore.version(file) == cacheParseVersion) return;
        cacheParse = Parser.parseFile(file);
        cacheParseFile = file;
        cacheParseVersion = FileStore.version(file);
    }

    @Override
    public List<SymbolInformation> documentSymbol(DocumentSymbolParams params) {
        var uri = params.textDocument.uri;
        if (!FileStore.isJavaFile(uri)) return List.of();
        var file = Paths.get(uri);
        updateCachedParse(file);
        var infos = cacheParse.documentSymbols();
        return infos;
    }

    @Override
    public List<CodeLens> codeLens(CodeLensParams params) {
        var uri = params.textDocument.uri;
        if (!FileStore.isJavaFile(uri)) return List.of();
        var file = Paths.get(uri);
        updateCachedParse(file);
        var declarations = cacheParse.codeLensDeclarations();
        var result = new ArrayList<CodeLens>();
        for (var d : declarations) {
            var range = cacheParse.range(d);
            if (range == Range.NONE) continue;
            var className = Parser.className(d);
            var memberName = Parser.memberName(d);
            // If test class or method, add "Run Test" code lens
            if (cacheParse.isTestClass(d)) {
                var arguments = new JsonArray();
                arguments.add(uri.toString());
                arguments.add(className);
                arguments.add(JsonNull.INSTANCE);
                var command = new Command("Run All Tests", "java.command.test.run", arguments);
                var lens = new CodeLens(range, command, null);
                result.add(lens);
                // TODO run all tests in file
                // TODO run all tests in package
            }
            if (cacheParse.isTestMethod(d)) {
                var arguments = new JsonArray();
                arguments.add(uri.toString());
                arguments.add(className);
                if (!memberName.isEmpty()) arguments.add(memberName);
                else arguments.add(JsonNull.INSTANCE);
                // 'Run Test' code lens
                var command = new Command("Run Test", "java.command.test.run", arguments);
                var lens = new CodeLens(range, command, null);
                result.add(lens);
                // 'Debug Test' code lens
                // TODO this could be a CPU hot spot
                var sourceRoots = new JsonArray();
                for (var path : FileStore.sourceRoots()) {
                    sourceRoots.add(path.toString());
                }
                arguments.add(sourceRoots);
                command = new Command("Debug Test", "java.command.test.debug", arguments);
                lens = new CodeLens(range, command, null);
                result.add(lens);
            }
        }
        return result;
    }

    @Override
    public CodeLens resolveCodeLens(CodeLens _unresolved) {
        return null;
    }

    @Override
    public List<TextEdit> formatting(DocumentFormattingParams params) {
        var edits = new ArrayList<TextEdit>();
        var file = Paths.get(params.textDocument.uri);
        var fixImports = new AutoFixImports(file).rewrite(compiler()).get(file);
        for (var e : fixImports) {
            edits.add(e);
        }
        var addOverrides = new AutoAddOverrides(file).rewrite(compiler()).get(file);
        for (var e : addOverrides) {
            edits.add(e);
        }
        return edits;
    }

    @Override
    public List<FoldingRange> foldingRange(FoldingRangeParams params) {
        if (!FileStore.isJavaFile(params.textDocument.uri)) return List.of();
        var file = Paths.get(params.textDocument.uri);
        updateCachedParse(file);
        return cacheParse.foldingRanges();
    }

    @Override
    public Optional<RenameResponse> prepareRename(TextDocumentPositionParams params) {
        if (!FileStore.isJavaFile(params.textDocument.uri)) return Optional.empty();
        LOG.info("Try to rename...");
        var file = Paths.get(params.textDocument.uri);
        try (var compile = compiler().compileBatch(List.of(new SourceFileObject(file)))) {
            var lines = compile.root(file).getLineMap();
            var position = lines.getPosition(params.position.line + 1, params.position.character + 1);
            var path = compile.findPath(file, position);
            var el = compile.element(path);
            if (el.isEmpty()) {
                LOG.info("...no element under cursor");
                return Optional.empty();
            }
            if (!canRename(el.get())) {
                LOG.info("...can't rename " + el.get());
                return Optional.empty();
            }
            var toFile = findElement(el.get());
            if (toFile.isEmpty()) {
                LOG.info("...can't find source for " + el.get());
                return Optional.empty();
            }
            var response = new RenameResponse();
            response.range = compile.range(path);
            response.placeholder = el.get().getSimpleName().toString();
            return Optional.of(response);
        }
    }

    private boolean canRename(Element rename) {
        switch (rename.getKind()) {
            case METHOD:
            case FIELD:
            case LOCAL_VARIABLE:
                return true;
            default:
                // TODO rename other types
                return false;
        }
    }

    @Override
    public WorkspaceEdit rename(RenameParams params) {
        var rw = createRewrite(params);
        var response = new WorkspaceEdit();
        var map = rw.rewrite(compiler());
        for (var editedFile : map.keySet()) {
            response.changes.put(editedFile.toUri(), List.of(map.get(editedFile)));
        }
        return response;
    }

    private Rewrite createRewrite(RenameParams params) {
        var file = Paths.get(params.textDocument.uri);
        try (var compile = compiler().compileBatch(List.of(new SourceFileObject(file)))) {
            var lines = compile.root(file).getLineMap();
            var position = lines.getPosition(params.position.line + 1, params.position.character + 1);
            var path = compile.findPath(file, position);
            var el = compile.element(path).get();
            switch (el.getKind()) {
                case METHOD:
                    return renameMethod(compile, (ExecutableElement) el, params.newName);
                case FIELD:
                    return renameField(compile, (VariableElement) el, params.newName);
                case LOCAL_VARIABLE:
                    return renameVariable(compile, (VariableElement) el, params.newName);
                default:
                    return Rewrite.NOT_SUPPORTED;
            }
        }
    }

    private RenameMethod renameMethod(CompileBatch compile, ExecutableElement method, String newName) {
        var parent = (TypeElement) method.getEnclosingElement();
        var className = parent.getQualifiedName().toString();
        var methodName = method.getSimpleName().toString();
        var erasedParameterTypes = new String[method.getParameters().size()];
        for (var i = 0; i < erasedParameterTypes.length; i++) {
            var type = method.getParameters().get(i).asType();
            erasedParameterTypes[i] = compile.types.erasure(type).toString();
        }
        return new RenameMethod(className, methodName, erasedParameterTypes, newName);
    }

    private RenameField renameField(CompileBatch compile, VariableElement field, String newName) {
        var parent = (TypeElement) field.getEnclosingElement();
        var className = parent.getQualifiedName().toString();
        var fieldName = field.getSimpleName().toString();
        return new RenameField(className, fieldName, newName);
    }

    private RenameVariable renameVariable(CompileBatch compile, VariableElement variable, String newName) {
        var path = compile.trees.getPath(variable);
        var file = Paths.get(path.getCompilationUnit().getSourceFile().toUri());
        var position = compile.sourcePositions().getStartPosition(path.getCompilationUnit(), path.getLeaf());
        return new RenameVariable(file, (int) position, newName);
    }

    private boolean uncheckedChanges = false;
    private Path lastEdited = Paths.get("");

    @Override
    public void didOpenTextDocument(DidOpenTextDocumentParams params) {
        FileStore.open(params);
        if (!FileStore.isJavaFile(params.textDocument.uri)) return;
        // So that subsequent documentSymbol and codeLens requests will be faster
        var file = Paths.get(params.textDocument.uri);
        updateCachedParse(file);
        lastEdited = file;
        uncheckedChanges = true;
    }

    @Override
    public void didChangeTextDocument(DidChangeTextDocumentParams params) {
        FileStore.change(params);
        lastEdited = Paths.get(params.textDocument.uri);
        uncheckedChanges = true;
    }

    @Override
    public void didCloseTextDocument(DidCloseTextDocumentParams params) {
        FileStore.close(params);

        if (FileStore.isJavaFile(params.textDocument.uri)) {
            // Clear diagnostics
            client.publishDiagnostics(new PublishDiagnosticsParams(params.textDocument.uri, List.of()));
        }
    }

    @Override
    public List<CodeAction> codeAction(CodeActionParams params) {
        var actions = new ArrayList<CodeAction>();
        for (var d : params.context.diagnostics) {
            var file = Paths.get(params.textDocument.uri);
            var newActions = codeActionForDiagnostic(file, d);
            actions.addAll(newActions);
        }
        return actions;
    }

    private List<CodeAction> codeActionForDiagnostic(Path file, Diagnostic d) {
        // TODO this should be done asynchronously using executeCommand
        switch (d.code) {
            case "unused_local":
                var toStatement = new ConvertVariableToStatement(file, findPosition(file, d.range.start));
                return createQuickFix("Convert to statement", toStatement);
            case "unused_field":
                var toBlock = new ConvertFieldToBlock(file, findPosition(file, d.range.start));
                return createQuickFix("Convert to block", toBlock);
            case "unused_class":
                var removeClass = new RemoveClass(file, findPosition(file, d.range.start));
                return createQuickFix("Remove class", removeClass);
            case "unused_method":
                var unusedMethod = findMethod(file, d.range);
                var removeMethod =
                        new RemoveMethod(
                                unusedMethod.className, unusedMethod.methodName, unusedMethod.erasedParameterTypes);
                return createQuickFix("Remove method", removeMethod);
            case "unused_throws":
                var shortExceptionName = extractRange(file, d.range);
                var notThrown = extractNotThrownExceptionName(d.message);
                var methodWithExtraThrow = findMethod(file, d.range);
                var removeThrow =
                        new RemoveException(
                                methodWithExtraThrow.className,
                                methodWithExtraThrow.methodName,
                                methodWithExtraThrow.erasedParameterTypes,
                                notThrown);
                return createQuickFix("Remove '" + shortExceptionName + "'", removeThrow);
            case "compiler.warn.unchecked.call.mbr.of.raw.type":
                var warnedMethod = findMethod(file, d.range);
                var suppressWarning =
                        new AddSuppressWarningAnnotation(
                                warnedMethod.className, warnedMethod.methodName, warnedMethod.erasedParameterTypes);
                return createQuickFix("Suppress 'unchecked' warning", suppressWarning);
            case "compiler.err.unreported.exception.need.to.catch.or.throw":
                var needsThrow = findMethod(file, d.range);
                var exceptionName = extractExceptionName(d.message);
                var addThrows =
                        new AddException(
                                needsThrow.className,
                                needsThrow.methodName,
                                needsThrow.erasedParameterTypes,
                                exceptionName);
                return createQuickFix("Add 'throws'", addThrows);
            case "compiler.err.cant.resolve.location":
                var simpleName = extractRange(file, d.range);
                var allImports = new ArrayList<CodeAction>();
                for (var qualifiedName : compiler().publicTopLevelTypes()) {
                    if (qualifiedName.endsWith("." + simpleName)) {
                        var title = "Import '" + qualifiedName + "'";
                        var addImport = new AddImport(file, qualifiedName);
                        allImports.addAll(createQuickFix(title, addImport));
                    }
                }
                return allImports;
            default:
                return List.of();
        }
    }

    private int findPosition(Path file, Position position) {
        var parse = Parser.parseFile(file);
        var lines = parse.root.getLineMap();
        return (int) lines.getPosition(position.line + 1, position.character + 1);
    }

    private MethodPtr findMethod(Path file, Range range) {
        try (var task = compiler().compile(file)) {
            var trees = Trees.instance(task.task);
            var types = task.task.getTypes();
            var position = task.root().getLineMap().getPosition(range.start.line + 1, range.start.character + 1);
            var tree = new FindMethodDeclarationAt(task.task).scan(task.root(), position);
            var path = trees.getPath(task.root(), tree);
            var method = (ExecutableElement) trees.getElement(path);
            var parent = (TypeElement) method.getEnclosingElement();
            var p = new MethodPtr();
            p.className = parent.getQualifiedName().toString();
            p.methodName = method.getSimpleName().toString();
            p.erasedParameterTypes = new String[method.getParameters().size()];
            for (var i = 0; i < p.erasedParameterTypes.length; i++) {
                var param = method.getParameters().get(i);
                var type = param.asType();
                var erased = types.erasure(type);
                p.erasedParameterTypes[i] = erased.toString();
            }
            return p;
        }
    }

    class MethodPtr {
        String className, methodName;
        String[] erasedParameterTypes;
    }

    private static final Pattern NOT_THROWN_EXCEPTION = Pattern.compile("^'((\\w+\\.)*\\w+)' is not thrown");

    private String extractNotThrownExceptionName(String message) {
        var matcher = NOT_THROWN_EXCEPTION.matcher(message);
        if (!matcher.find()) {
            LOG.warning(String.format("`%s` doesn't match `%s`", message, NOT_THROWN_EXCEPTION));
            return "";
        }
        return matcher.group(1);
    }

    private static final Pattern UNREPORTED_EXCEPTION = Pattern.compile("unreported exception ((\\w+\\.)*\\w+)");

    private String extractExceptionName(String message) {
        var matcher = UNREPORTED_EXCEPTION.matcher(message);
        if (!matcher.find()) {
            LOG.warning(String.format("`%s` doesn't match `%s`", message, UNREPORTED_EXCEPTION));
            return "";
        }
        return matcher.group(1);
    }

    private String extractRange(Path file, Range range) {
        var parse = Parser.parseFile(file);
        var contents = FileStore.contents(file);
        var start = (int) parse.root.getLineMap().getPosition(range.start.line + 1, range.start.character + 1);
        var end = (int) parse.root.getLineMap().getPosition(range.end.line + 1, range.end.character + 1);
        return contents.substring(start, end);
    }

    private List<CodeAction> createQuickFix(String title, Rewrite rewrite) {
        var edits = rewrite.rewrite(compiler());
        if (edits == Rewrite.CANCELLED) {
            return List.of();
        }
        var a = new CodeAction();
        a.kind = CodeActionKind.QuickFix;
        a.title = title;
        a.edit = new WorkspaceEdit();
        for (var file : edits.keySet()) {
            a.edit.changes.put(file.toUri(), List.of(edits.get(file)));
        }
        return List.of(a);
    }

    @Override
    public void didSaveTextDocument(DidSaveTextDocumentParams params) {
        if (FileStore.isJavaFile(params.textDocument.uri)) {
            // Re-lint all active documents
            lint(FileStore.activeDocuments());
        }
    }

    @Override
    public void doAsyncWork() {
        if (uncheckedChanges && FileStore.activeDocuments().contains(lastEdited)) {
            lint(List.of(lastEdited));
            uncheckedChanges = false;
        }
    }

    private static final Logger LOG = Logger.getLogger("main");
}
