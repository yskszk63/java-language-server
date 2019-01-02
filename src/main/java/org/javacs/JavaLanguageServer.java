package org.javacs;

import com.google.gson.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.javacs.lsp.*;

class JavaLanguageServer extends LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");

    // TODO allow multiple workspace roots
    private Path workspaceRoot;
    private SourcePath sourcePath;
    private final LanguageClient client;
    private Set<String> externalDependencies = Set.of();
    private Set<Path> classPath = Set.of();

    JavaCompilerService compiler;
    private final Map<URI, VersionedContent> activeDocuments = new HashMap<>();

    private static int severity(Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return DiagnosticSeverity.Error;
            case WARNING:
            case MANDATORY_WARNING:
                return DiagnosticSeverity.Warning;
            case NOTE:
            case OTHER:
            default:
                return DiagnosticSeverity.Information;
        }
    }

    private static Position position(String content, long offset) {
        int line = 0, column = 0;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') {
                line++;
                column = 0;
            } else column++;
        }
        return new Position(line, column);
    }

    void publishDiagnostics(Collection<URI> files, List<Diagnostic<? extends JavaFileObject>> javaDiagnostics) {
        for (var f : files) {
            List<org.javacs.lsp.Diagnostic> ds = new ArrayList<>();
            for (var j : javaDiagnostics) {
                if (j.getSource() == null) {
                    LOG.warning("No source in warning " + j.getMessage(null));
                    continue;
                }

                var uri = j.getSource().toUri();
                if (uri.equals(f)) {
                    var content = contents(uri).content;
                    var start = position(content, j.getStartPosition());
                    var end = position(content, j.getEndPosition());
                    var d = new org.javacs.lsp.Diagnostic();
                    d.severity = severity(j.getKind());
                    d.range = new Range(start, end);
                    d.code = j.getCode();
                    d.message = j.getMessage(null);
                    ds.add(d);
                }
            }
            client.publishDiagnostics(new PublishDiagnosticsParams(f, ds));
        }
    }

    void lint(Collection<URI> uris) {
        var message = new StringJoiner(", ");
        for (var uri : uris) {
            var path = uri.getPath();
            var name = Paths.get(path).getFileName();
            message.add(name.toString());
        }
        LOG.info("Lint " + message);
        publishDiagnostics(uris, compiler.compileBatch(uris).lint());
    }

    private static final Gson gson = new Gson();

    private void javaStartProgress(JavaStartProgressParams params) {
        client.customNotification("java/startProgress", gson.toJsonTree(params));
    }

    private void javaReportProgress(JavaReportProgressParams params) {
        client.customNotification("java/reportProgress", gson.toJsonTree(params));
    }

    private void javaEndProgress() {
        client.customNotification("java/endProgress", JsonNull.INSTANCE);
    }

    private JavaCompilerService createCompiler() {
        Objects.requireNonNull(workspaceRoot, "Can't create compiler because workspaceRoot has not been initialized");

        javaStartProgress(new JavaStartProgressParams("Configure javac"));
        javaReportProgress(new JavaReportProgressParams("Finding source roots"));

        // If classpath is specified by the user, don't infer anything
        if (!classPath.isEmpty()) {
            javaEndProgress();
            return new JavaCompilerService(
                    sourcePath.sourceRoots(), sourcePath::allJavaFiles, classPath, Collections.emptySet());
        }
        // Otherwise, combine inference with user-specified external dependencies
        else {
            var infer = new InferConfig(workspaceRoot, externalDependencies);

            javaReportProgress(new JavaReportProgressParams("Inferring class path"));
            var classPath = infer.classPath();

            javaReportProgress(new JavaReportProgressParams("Inferring doc path"));
            var docPath = infer.buildDocPath();

            javaEndProgress();
            return new JavaCompilerService(sourcePath.sourceRoots(), sourcePath::allJavaFiles, classPath, docPath);
        }
    }

    void setExternalDependencies(Set<String> externalDependencies) {
        var changed =
                this.externalDependencies.isEmpty()
                        != externalDependencies.isEmpty(); // TODO shouldn't this be any change?
        this.externalDependencies = externalDependencies;
        if (changed) this.compiler = createCompiler();
    }

    void setClassPath(Set<Path> classPath) {
        var changed = this.classPath.isEmpty() != classPath.isEmpty(); // TODO shouldn't this be any change?
        this.classPath = classPath;
        if (changed) this.compiler = createCompiler();
    }

    @Override
    public InitializeResult initialize(InitializeParams params) {
        this.workspaceRoot = Paths.get(params.rootUri);
        this.sourcePath = new SourcePath(Set.of(workspaceRoot));

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
        codeLensOptions.addProperty("resolveProvider", true);
        c.add("codeLensProvider", codeLensOptions);
        c.addProperty("foldingRangeProvider", true);

        return new InitializeResult(c);
    }

    @Override
    public void initialized() {
        this.compiler = createCompiler();

        // Register for didChangeWatchedFiles notifications
        var options = new JsonObject();
        var watchers = new JsonArray();
        var watchJava = new JsonObject();
        watchJava.addProperty("globPattern", "**/*.java");
        watchers.add(watchJava);
        options.add("watchers", watchers);
        client.registerCapability("workspace/didChangeWatchedFiles", gson.toJsonTree(options));
    }

    @Override
    public void shutdown() {}

    public JavaLanguageServer(LanguageClient client) {
        this.client = client;
    }

    @Override
    public List<SymbolInformation> workspaceSymbols(WorkspaceSymbolParams params) {
        var list = new ArrayList<SymbolInformation>();
        for (var s : compiler.findSymbols(params.query, 50)) {
            var i = asSymbolInformation(s);
            list.add(i);
        }
        return list;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams change) {
        var settings = (JsonObject) change.settings;
        var java = settings.getAsJsonObject("java");

        var externalDependencies = java.getAsJsonArray("externalDependencies");
        var strings = new HashSet<String>();
        for (var each : externalDependencies) strings.add(each.getAsString());
        setExternalDependencies(strings);

        var classPath = java.getAsJsonArray("classPath");
        var paths = new HashSet<Path>();
        for (var each : classPath) paths.add(Paths.get(each.getAsString()).toAbsolutePath());
        setClassPath(paths);
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // TODO update config when pom.xml changes
        var changed = false;
        var created = new HashSet<Path>();
        var deleted = new HashSet<Path>();
        for (var c : params.changes) {
            if (!isJavaFile(c.uri)) continue;
            var file = Paths.get(c.uri);
            switch (c.type) {
                case FileChangeType.Created:
                    created.add(file);
                    break;
                case FileChangeType.Changed:
                    if (sourcePath.update(file)) changed = true;
                    break;
                case FileChangeType.Deleted:
                    deleted.add(file);
                    break;
            }
        }
        if (sourcePath.create(created)) changed = true;
        if (sourcePath.delete(deleted)) changed = true;
        if (changed) this.compiler = createCompiler();
    }

    private Integer completionItemKind(Element e) {
        switch (e.getKind()) {
            case ANNOTATION_TYPE:
                return CompletionItemKind.Interface;
            case CLASS:
                return CompletionItemKind.Class;
            case CONSTRUCTOR:
                return CompletionItemKind.Constructor;
            case ENUM:
                return CompletionItemKind.Enum;
            case ENUM_CONSTANT:
                return CompletionItemKind.EnumMember;
            case EXCEPTION_PARAMETER:
                return CompletionItemKind.Variable;
            case FIELD:
                return CompletionItemKind.Field;
            case STATIC_INIT:
            case INSTANCE_INIT:
                return CompletionItemKind.Function;
            case INTERFACE:
                return CompletionItemKind.Interface;
            case LOCAL_VARIABLE:
                return CompletionItemKind.Variable;
            case METHOD:
                return CompletionItemKind.Method;
            case PACKAGE:
                return CompletionItemKind.Module;
            case PARAMETER:
                return CompletionItemKind.Variable;
            case RESOURCE_VARIABLE:
                return CompletionItemKind.Variable;
            case TYPE_PARAMETER:
                return CompletionItemKind.TypeParameter;
            case OTHER:
            default:
                return null;
        }
    }

    private boolean isMemberOfObject(Element e) {
        var parent = e.getEnclosingElement();
        if (parent instanceof TypeElement) {
            var type = (TypeElement) parent;
            return type.getQualifiedName().contentEquals("java.lang.Object");
        }
        return false;
    }

    /** Cache of completions from the last call to `completion` */
    private final Map<String, Completion> lastCompletions = new HashMap<>();

    @Override
    public Optional<CompletionList> completion(TextDocumentPositionParams position) {
        var started = Instant.now();
        var uri = position.textDocument.uri;
        if (!isJavaFile(uri)) return Optional.empty();
        var content = contents(uri).content;
        var line = position.position.line + 1;
        var column = position.position.character + 1;
        // Figure out what kind of completion we want to do
        var maybeCtx = compiler.parseFile(uri, content).completionContext(line, column);
        if (!maybeCtx.isPresent()) {
            var items = new ArrayList<CompletionItem>();
            for (var name : CompileFocus.TOP_LEVEL_KEYWORDS) {
                var i = new CompletionItem();
                i.label = name;
                i.kind = CompletionItemKind.Keyword;
                i.detail = "keyword";
                items.add(i);
            }
            return Optional.of(new CompletionList(true, items));
        }
        // Compile again, focusing on a region that depends on what type of completion we want to do
        var ctx = maybeCtx.get();
        var focus = compiler.compileFocus(uri, content, ctx.line, ctx.character);
        // Do a specific type of completion
        List<Completion> cs;
        boolean isIncomplete;
        switch (ctx.kind) {
            case MemberSelect:
                cs = focus.completeMembers(false);
                isIncomplete = false;
                break;
            case MemberReference:
                cs = focus.completeMembers(true);
                isIncomplete = false;
                break;
            case Identifier:
                cs = focus.completeIdentifiers(ctx.inClass, ctx.inMethod, ctx.partialName);
                isIncomplete = cs.size() >= CompileFocus.MAX_COMPLETION_ITEMS;
                break;
            case Annotation:
                cs = focus.completeAnnotations(ctx.partialName);
                isIncomplete = cs.size() >= CompileFocus.MAX_COMPLETION_ITEMS;
                break;
            case Case:
                cs = focus.completeCases();
                isIncomplete = false;
                break;
            default:
                throw new RuntimeException("Unexpected completion context " + ctx.kind);
        }
        // Convert to CompletionItem
        var result = new ArrayList<CompletionItem>();
        for (var c : cs) {
            var i = new CompletionItem();
            var id = UUID.randomUUID().toString();
            i.data = new JsonPrimitive(id);
            lastCompletions.put(id, c);
            if (c.element != null) {
                i.label = c.element.getSimpleName().toString();
                i.kind = completionItemKind(c.element);
                // Detailed name will be resolved later, using docs to fill in method names
                if (!(c.element instanceof ExecutableElement)) {
                    i.detail = ShortTypePrinter.print(c.element.asType());
                }
                // TODO prioritize based on usage?
                // TODO prioritize based on scope
                if (isMemberOfObject(c.element)) {
                    i.sortText = 9 + i.label;
                } else {
                    i.sortText = 2 + i.label;
                }
            } else if (c.packagePart != null) {
                i.label = c.packagePart.name;
                i.kind = CompletionItemKind.Module;
                i.detail = c.packagePart.fullName;
                i.sortText = 2 + i.label;
            } else if (c.keyword != null) {
                i.label = c.keyword;
                i.kind = CompletionItemKind.Keyword;
                i.detail = "keyword";
                i.sortText = 3 + i.label;
            } else if (c.className != null) {
                i.label = Parser.lastName(c.className.name);
                i.kind = CompletionItemKind.Class;
                i.detail = c.className.name;
                if (c.className.isImported) {
                    i.sortText = 2 + i.label;
                } else {
                    i.sortText = 4 + i.label;
                }
            } else if (c.snippet != null) {
                i.label = c.snippet.label;
                i.kind = CompletionItemKind.Snippet;
                i.insertText = c.snippet.snippet;
                i.insertTextFormat = InsertTextFormat.Snippet;
                i.sortText = 1 + i.label;
            } else {
                throw new RuntimeException(c + " is not valid");
            }

            result.add(i);
        }
        // Log timing
        var elapsedMs = Duration.between(started, Instant.now()).toMillis();
        if (isIncomplete) LOG.info(String.format("Found %d items (incomplete) in %,d ms", result.size(), elapsedMs));
        else LOG.info(String.format("...found %d items in %,d ms", result.size(), elapsedMs));

        return Optional.of(new CompletionList(isIncomplete, result));
    }

    private Optional<MarkupContent> findDocs(Ptr ptr) {
        LOG.info(String.format("Find docs for `%s`...", ptr));

        // Find el in the doc path
        var file = compiler.docs().find(ptr);
        if (!file.isPresent()) return Optional.empty();
        // Parse file and find el
        var parse = compiler.docs().parse(file.get());
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return Optional.empty();
        // Parse the doctree associated with el
        var docTree = parse.doc(path.get());
        ;
        var string = asMarkupContent(docTree);
        return Optional.of(string);
    }

    private Optional<String> findMethodDetails(ExecutableElement method) {
        LOG.info(String.format("Find details for method `%s`...", method));

        // TODO find and parse happens twice between findDocs and findMethodDetails
        // Find method in the doc path
        var ptr = new Ptr(method);
        var file = compiler.docs().find(ptr);
        if (!file.isPresent()) return Optional.empty();
        // Parse file and find method
        var parse = compiler.docs().parse(file.get());
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return Optional.empty();
        // Should be a MethodTree
        var tree = path.get().getLeaf();
        if (!(tree instanceof MethodTree)) {
            LOG.warning(String.format("...method `%s` associated with non-method tree `%s`", method, tree));
            return Optional.empty();
        }
        // Write description of method using info from source
        var methodTree = (MethodTree) tree;
        var args = new StringJoiner(", ");
        for (var p : methodTree.getParameters()) {
            args.add(p.getName());
        }
        var details = String.format("%s %s(%s)", methodTree.getReturnType(), methodTree.getName(), args);
        return Optional.of(details);
    }

    private String defaultDetails(ExecutableElement method) {
        var args = new StringJoiner(", ");
        var missingParamNames =
                method.getParameters().stream().allMatch(p -> p.getSimpleName().toString().matches("arg\\d+"));
        for (var p : method.getParameters()) {
            if (missingParamNames) args.add(ShortTypePrinter.print(p.asType()));
            else args.add(p.getSimpleName().toString());
        }
        return String.format("%s %s(%s)", ShortTypePrinter.print(method.getReturnType()), method.getSimpleName(), args);
    }

    private String asMarkdown(List<? extends DocTree> lines) {
        var join = new StringJoiner("\n");
        for (var l : lines) join.add(l.toString());
        var html = join.toString();
        return Docs.htmlToMarkdown(html);
    }

    private String asMarkdown(DocCommentTree comment) {
        var lines = comment.getFirstSentence();
        return asMarkdown(lines);
    }

    private MarkupContent asMarkupContent(DocCommentTree comment) {
        var markdown = asMarkdown(comment);
        var content = new MarkupContent();
        content.kind = MarkupKind.Markdown;
        content.value = markdown;
        return content;
    }

    @Override
    public CompletionItem resolveCompletionItem(CompletionItem unresolved) {
        if (unresolved.data == null) return unresolved;
        var idJson = (JsonPrimitive) unresolved.data;
        var id = idJson.getAsString();
        var cached = lastCompletions.get(id);
        if (cached == null) {
            LOG.warning("CompletionItem " + id + " was not in the cache");
            return unresolved;
        }
        if (cached.element != null) {
            if (cached.element instanceof ExecutableElement) {
                var method = (ExecutableElement) cached.element;
                unresolved.detail = findMethodDetails(method).orElse(defaultDetails(method));
            }
            var markdown = findDocs(new Ptr(cached.element));
            if (markdown.isPresent()) {
                unresolved.documentation = markdown.get();
            }
        } else if (cached.className != null) {
            var packageName = Parser.mostName(cached.className.name);
            var className = Parser.lastName(cached.className.name);
            var ptr = Ptr.toClass(packageName, className);
            var markdown = findDocs(ptr);
            if (markdown.isPresent()) unresolved.documentation = markdown.get();
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
        result.append(" ").append(ShortTypePrinter.print(t.asType()));
        var superType = ShortTypePrinter.print(t.getSuperclass());
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
            return ShortTypePrinter.printMethod(m);
        } else if (e instanceof VariableElement) {
            var v = (VariableElement) e;
            return ShortTypePrinter.print(v.asType()) + " " + v;
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

    private Optional<String> hoverDocs(Element e) {
        var ptr = new Ptr(e);
        var file = compiler.docs().find(ptr);
        if (!file.isPresent()) return Optional.empty();
        var parse = compiler.docs().parse(file.get());
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return Optional.empty();
        var doc = parse.doc(path.get());
        var md = asMarkdown(doc);
        return Optional.of(md);
    }

    // TODO change name
    private CompileFile hoverCache;

    // TODO take only URI and invalidate based on version
    private void updateHoverCache(URI uri, String contents) {
        if (hoverCache == null || !hoverCache.file.equals(uri) || !hoverCache.contents.equals(contents)) {
            LOG.info("File has changed since last hover, recompiling");
            hoverCache = compiler.compileFile(uri, contents);
        }
    }

    @Override
    public Optional<Hover> hover(TextDocumentPositionParams position) {
        // Compile entire file if it's changed since last hover
        var uri = position.textDocument.uri;
        if (!isJavaFile(uri)) return Optional.empty();
        var content = contents(uri).content;
        updateHoverCache(uri, content);

        // Find element undeer cursor
        var line = position.position.line + 1;
        var column = position.position.character + 1;
        var el = hoverCache.element(line, column);
        if (!el.isPresent()) return Optional.empty();

        // Add code hover message
        var result = new ArrayList<MarkedString>();
        var code = hoverCode(el.get());
        result.add(new MarkedString("java.hover", code));
        // Add docs hover message
        var docs = hoverDocs(el.get());
        if (docs.isPresent()) {
            result.add(new MarkedString("markdown", docs.get()));
        }

        return Optional.of(new Hover(result));
    }

    private SignatureInformation asSignatureInformation(ExecutableElement e) {
        // Figure out parameter info from source or from ExecutableElement
        var i = new SignatureInformation();
        var ptr = new Ptr(e);
        var ps = signatureParamsFromDocs(ptr).orElse(signatureParamsFromMethod(e));
        i.parameters = ps;

        // Compute label from params (which came from either source or ExecutableElement)
        var name = e.getSimpleName();
        if (name.contentEquals("<init>")) name = e.getEnclosingElement().getSimpleName();
        var args = new StringJoiner(", ");
        for (var p : ps) {
            args.add(p.label);
        }
        i.label = name + "(" + args + ")";

        return i;
    }

    private List<ParameterInformation> signatureParamsFromMethod(ExecutableElement e) {
        var missingParamNames = ShortTypePrinter.missingParamNames(e);
        var ps = new ArrayList<ParameterInformation>();
        for (var v : e.getParameters()) {
            var p = new ParameterInformation();
            if (missingParamNames) p.label = ShortTypePrinter.print(v.asType());
            else p.label = v.getSimpleName().toString();
            ps.add(p);
        }
        return ps;
    }

    private Optional<List<ParameterInformation>> signatureParamsFromDocs(Ptr ptr) {
        // Find the file ptr point to, and parse it
        var file = compiler.docs().find(ptr);
        if (!file.isPresent()) return Optional.empty();
        var parse = compiler.docs().parse(file.get());
        // Find the tree
        var path = parse.fuzzyFind(ptr);
        if (!path.isPresent()) return Optional.empty();
        if (!(path.get().getLeaf() instanceof MethodTree)) return Optional.empty();
        var method = (MethodTree) path.get().getLeaf();
        // Find the docstring on method, or empty doc if there is none
        var doc = parse.doc(path.get());
        // Get param docs from @param tags
        var ps = new ArrayList<ParameterInformation>();
        var paramComments = new HashMap<String, String>();
        for (var tag : doc.getBlockTags()) {
            if (tag.getKind() == DocTree.Kind.PARAM) {
                var param = (ParamTree) tag;
                paramComments.put(param.getName().toString(), asMarkdown(param.getDescription()));
            }
        }
        // Get param names from source
        for (var param : method.getParameters()) {
            var info = new ParameterInformation();
            var name = param.getName().toString();
            info.label = name;
            if (paramComments.containsKey(name)) {
                var markdown = paramComments.get(name);
                info.documentation = new MarkupContent("markdown", markdown);
            } else {
                var markdown = Objects.toString(param.getType(), "");
                info.documentation = new MarkupContent("markdown", markdown);
            }
            ps.add(info);
        }
        return Optional.of(ps);
    }

    private SignatureHelp asSignatureHelp(MethodInvocation invoke) {
        // TODO use docs to get parameter names
        var sigs = new ArrayList<SignatureInformation>();
        for (var e : invoke.overloads) {
            sigs.add(asSignatureInformation(e));
        }
        var activeSig = invoke.activeMethod.map(invoke.overloads::indexOf).orElse(0);
        return new SignatureHelp(sigs, activeSig, invoke.activeParameter);
    }

    @Override
    public Optional<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        var uri = position.textDocument.uri;
        if (!isJavaFile(uri)) return Optional.empty();
        var content = contents(uri).content;
        var line = position.position.line + 1;
        var column = position.position.character + 1;
        var focus = compiler.compileFocus(uri, content, line, column);
        var help = focus.methodInvocation().map(this::asSignatureHelp);
        return help;
    }

    @Override
    public List<Location> gotoDefinition(TextDocumentPositionParams position) {
        var fromUri = position.textDocument.uri;
        if (!isJavaFile(fromUri)) return List.of();
        var fromLine = position.position.line + 1;
        var fromColumn = position.position.character + 1;
        var fromContent = contents(fromUri).content;

        // Compile from-file and identify element under cursor
        LOG.info(String.format("Go-to-def at %s:%d...", fromUri, fromLine));
        updateHoverCache(fromUri, fromContent);
        var toEl = hoverCache.element(fromLine, fromColumn);
        if (!toEl.isPresent()) {
            LOG.info(String.format("...no element at cursor"));
            return List.of();
        }

        // Figure out what file toEl is declared in
        LOG.info(String.format("...looking for definition of `%s`", toEl.get()));
        var toUri = hoverCache.declaringFile(toEl.get());
        if (!toUri.isPresent()) {
            LOG.info(String.format("...couldn't find declaring file, giving up"));
            return List.of();
        }
        if (!isJavaFile(toUri.get())) {
            LOG.info(String.format("...declaring file %s isn't a .java file", toUri));
            return List.of();
        }

        // Compile fromUri and toUri together
        Optional<Range> toRange;
        if (toUri.get().equals(fromUri)) {
            LOG.info("...definition is in the same file, using cached compilation");

            var toPath = hoverCache.path(toEl.get());
            if (!toPath.isPresent()) {
                LOG.warning(String.format("...couldn't locate `%s` in %s", toEl, toUri.get()));
                return List.of();
            }
            toRange = hoverCache.range(toPath.get());
            if (!toRange.isPresent()) {
                LOG.info(String.format("...couldn't find `%s` in %s", toPath.get(), toUri));
                return List.of();
            }
        } else {
            LOG.info(
                    String.format(
                            "...compiling %s and %s together", Parser.fileName(fromUri), Parser.fileName(toUri.get())));

            var both = Map.of(fromUri, contents(fromUri).content, toUri.get(), contents(toUri.get()).content);
            var batch = compiler.compileBatch(both);
            var toElAgain = batch.element(fromUri, fromLine, fromColumn).get();
            var toPath = batch.path(toElAgain);
            if (!toPath.isPresent()) {
                LOG.warning(String.format("...couldn't locate `%s` in %s", toEl, toUri.get()));
                return List.of();
            }
            toRange = batch.range(toPath.get());
            if (!toRange.isPresent()) {
                LOG.info(String.format("...couldn't find `%s` in %s", toPath.get(), toUri));
                return List.of();
            }
        }

        var to = new Location(toUri.get(), toRange.get());
        return List.of(to);
    }

    class Progress implements ReportProgress, AutoCloseable {
        @Override
        public void start(String message) {
            javaStartProgress(new JavaStartProgressParams(message));
        }

        @Override
        public void progress(String message, int n, int total) {
            if (n == 0) {
                javaReportProgress(new JavaReportProgressParams(message));
            } else {
                var increment = percent(n, total) > percent(n - 1, total) ? 1 : 0;
                javaReportProgress(new JavaReportProgressParams(message, increment));
            }
        }

        private int percent(int n, int d) {
            double nD = n, dD = d;
            double ratio = nD / dD;
            return (int) (ratio * 100);
        }

        @Override
        public void close() {
            javaEndProgress();
        }
    }

    @Override
    public List<Location> findReferences(ReferenceParams position) {
        var toUri = position.textDocument.uri;
        if (!isJavaFile(toUri)) return List.of();
        var toContent = contents(toUri).content;
        updateHoverCache(toUri, toContent);
        var toLine = position.position.line + 1;
        var toColumn = position.position.character + 1;
        var toEl = hoverCache.element(toLine, toColumn);
        if (!toEl.isPresent()) {
            LOG.warning(String.format("No element under cursor %s(%d,%d)", toUri.getPath(), toLine, toColumn));
            return List.of();
        }
        var fromFiles = compiler.potentialReferences(toEl.get());
        if (fromFiles.isEmpty()) return List.of();
        var batch = compiler.compileBatch(fromFiles);
        var fromTreePaths = batch.references(toEl.get()); // Why does this work? These are two different batches
        var result = new ArrayList<Location>();
        for (var path : fromTreePaths) {
            var fromUri = path.getCompilationUnit().getSourceFile().toUri();
            var fromRange = batch.range(path);
            if (!fromRange.isPresent()) {
                LOG.warning(String.format("Couldn't locate `%s`", path.getLeaf()));
                continue;
            }
            var from = new Location(fromUri, fromRange.get());
            result.add(from);
        }
        return result;
    }

    private ParseFile cacheParse;
    private URI cacheParseFile = URI.create("file:///NONE");;
    private int cacheParseVersion = -1;

    private void updateCachedParse(URI file) {
        if (file.equals(cacheParseFile) && contents(file).version == cacheParseVersion) return;
        LOG.info(String.format("Updating cached parse file to %s", file));
        var contents = contents(file);
        cacheParse = compiler.parseFile(file, contents.content);
        cacheParseFile = file;
        cacheParseVersion = contents.version;
    }

    @Override
    public List<SymbolInformation> documentSymbol(DocumentSymbolParams params) {
        var uri = params.textDocument.uri;
        if (!isJavaFile(uri)) return List.of();
        updateCachedParse(uri);
        var paths = cacheParse.documentSymbols();
        var infos = new ArrayList<SymbolInformation>();
        for (var p : paths) {
            infos.add(asSymbolInformation(p));
        }
        return infos;
    }

    static SymbolInformation asSymbolInformation(TreePath path) {
        var i = new SymbolInformation();
        var t = path.getLeaf();
        i.kind = asSymbolKind(t.getKind());
        i.name = symbolName(t);
        i.containerName = containerName(path);
        i.location = Parser.location(path);
        return i;
    }

    private static Integer asSymbolKind(Tree.Kind k) {
        switch (k) {
            case ANNOTATION_TYPE:
            case CLASS:
                return SymbolKind.Class;
            case ENUM:
                return SymbolKind.Enum;
            case INTERFACE:
                return SymbolKind.Interface;
            case METHOD:
                return SymbolKind.Method;
            case TYPE_PARAMETER:
                return SymbolKind.TypeParameter;
            case VARIABLE:
                // This method is used for symbol-search functionality,
                // where we only return fields, not local variables
                return SymbolKind.Field;
            default:
                return null;
        }
    }

    private static String containerName(TreePath path) {
        var parent = path.getParentPath();
        while (parent != null) {
            var t = parent.getLeaf();
            if (t instanceof ClassTree) {
                var c = (ClassTree) t;
                return c.getSimpleName().toString();
            } else if (t instanceof CompilationUnitTree) {
                var c = (CompilationUnitTree) t;
                return Objects.toString(c.getPackageName(), "");
            } else {
                parent = parent.getParentPath();
            }
        }
        return null;
    }

    private static String symbolName(Tree t) {
        if (t instanceof ClassTree) {
            var c = (ClassTree) t;
            return c.getSimpleName().toString();
        } else if (t instanceof MethodTree) {
            var m = (MethodTree) t;
            return m.getName().toString();
        } else if (t instanceof VariableTree) {
            var v = (VariableTree) t;
            return v.getName().toString();
        } else {
            LOG.warning("Don't know how to create SymbolInformation from " + t);
            return "???";
        }
    }

    @Override
    public List<CodeLens> codeLens(CodeLensParams params) {
        // TODO just create a blank code lens on every method, then resolve it async
        var uri = params.textDocument.uri;
        if (!isJavaFile(uri)) return List.of();
        updateCachedParse(uri);
        var declarations = cacheParse.declarations();
        var result = new ArrayList<CodeLens>();
        for (var d : declarations) {
            var range = cacheParse.range(d);
            if (!range.isPresent()) continue;
            var className = JavaCompilerService.className(d);
            var memberName = JavaCompilerService.memberName(d);
            // If test class or method, add "Run Test" code lens
            if (cacheParse.isTestClass(d)) {
                var arguments = new JsonArray();
                arguments.add(uri.toString());
                arguments.add(className);
                arguments.add(JsonNull.INSTANCE);
                var command = new Command("Run All Tests", "java.command.test.run", arguments);
                var lens = new CodeLens(range.get(), command, null);
                result.add(lens);
                // TODO run all tests in file
                // TODO run all tests in package
            } else if (cacheParse.isTestMethod(d)) {
                var arguments = new JsonArray();
                arguments.add(uri.toString());
                arguments.add(className);
                if (memberName.isPresent()) arguments.add(memberName.get());
                else arguments.add(JsonNull.INSTANCE);
                var command = new Command("Run Test", "java.command.test.run", arguments);
                var lens = new CodeLens(range.get(), command, null);
                result.add(lens);
            }
            // Unresolved "_ references" code lens
            var start = range.get().start;
            var line = start.line;
            var character = start.character;
            var data = new JsonArray();
            data.add("java.command.findReferences");
            data.add(uri.toString());
            data.add(line);
            data.add(character);
            var lens = new CodeLens(range.get(), null, data);
            result.add(lens);
        }
        return result;
    }

    private Map<Ptr, Integer> cacheCountReferences = Collections.emptyMap();
    private URI cacheCountReferencesFile = URI.create("file:///NONE");
    private int cacheCountReferencesVersion = -1;

    private void updateCacheCountReferences(URI current) {
        if (cacheCountReferencesFile.equals(current) && cacheCountReferencesVersion == contents(current).version)
            return;
        LOG.info(String.format("Update cached reference count to %s...", current));
        var contents = contents(current);
        try (var progress = new Progress()) {
            cacheCountReferences = countReferences(current, contents.content, progress);
        }
        cacheCountReferencesFile = current;
        cacheCountReferencesVersion = contents.version;
    }

    private Map<URI, Index> index = new HashMap<>();

    private void updateIndex(Collection<URI> possible, ReportProgress progress) {
        LOG.info(String.format("Check %d files for modifications compared to index...", possible.size()));

        // signatureMatches tests if edits to the current file invalidate an index
        var signatureMatches = hoverCache.signatureMatches();
        // Figure out all files that have been changed, or contained errors at the time they were indexed
        var outOfDate = new ArrayList<URI>();
        var hasError = new ArrayList<URI>();
        var wrongSig = new ArrayList<URI>();
        for (var p : possible) {
            var i = index.getOrDefault(p, Index.EMPTY);
            var modified = Instant.ofEpochMilli(new File(p).lastModified());
            // TODO can modified rewind when you checkout a branch?
            if (modified.isAfter(i.modified)) outOfDate.add(p);
            else if (i.containsError) hasError.add(p);
            else if (!signatureMatches.test(i.refs)) wrongSig.add(p);
        }
        if (outOfDate.size() > 0) LOG.info(String.format("... %d files are out-of-date", outOfDate.size()));
        if (hasError.size() > 0) LOG.info(String.format("... %d files contain errors", hasError.size()));
        if (hasError.size() > 0)
            LOG.info(String.format("... %d files refer to methods that have changed", wrongSig.size()));

        // If there's nothing to update, return
        var needsUpdate = new ArrayList<URI>();
        needsUpdate.addAll(outOfDate);
        needsUpdate.addAll(hasError);
        if (needsUpdate.isEmpty()) return;

        // If there's more than 1 file, report progress
        if (needsUpdate.size() > 1) { // TODO this could probably be tuned to be higher
            progress.start(String.format("Index %d files", needsUpdate.size()));
        } else {
            progress = ReportProgress.EMPTY;
        }

        // Compile in a batch and update the index
        var counts = compiler.compileBatch(needsUpdate, progress).countReferences();
        index.putAll(counts);
    }

    public Map<Ptr, Integer> countReferences(URI file, String contents, ReportProgress progress) {
        updateHoverCache(file, contents(file).content);

        // List all files that import file
        var toPackage = Objects.toString(hoverCache.root.getPackageName(), "");
        var toClasses = hoverCache.allClassNames();
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

    // TODO should probably cache this
    private Collection<URI> potentialReferencesToClasses(String toPackage, List<String> toClasses) {
        // Filter for files that import toPackage.toClass
        var result = new LinkedHashSet<URI>();
        for (var file : sourcePath.allJavaFiles()) {
            if (importsAnyClass(toPackage, toClasses, file)) {
                result.add(file.toUri());
            }
        }
        return result;
    }

    private static boolean importsAnyClass(String toPackage, List<String> toClasses, Path file) {
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

    @Override
    public CodeLens resolveCodeLens(CodeLens unresolved) {
        // TODO This is pretty klugey, should happen asynchronously after CodeLenses are shown
        if (!recentlyOpened.isEmpty()) {
            lint(recentlyOpened);
            recentlyOpened.clear();
        }
        // Unpack data
        var data = unresolved.data;
        var command = data.get(0).getAsString();
        assert command.equals("java.command.findReferences");
        var uriString = data.get(1).getAsString();
        var uri = URI.create(uriString);
        var line = data.get(2).getAsInt() + 1;
        var character = data.get(3).getAsInt() + 1;
        // Update command
        var title = countReferencesTitle(uri, line, character);
        var arguments = new JsonArray();
        arguments.add(uri.toString());
        arguments.add(line - 1);
        arguments.add(character - 1);
        unresolved.command = new Command(title, command, arguments);

        return unresolved;
    }

    private String countReferencesTitle(URI uri, int line, int character) {
        updateHoverCache(uri, contents(uri).content);
        var el = hoverCache.element(line, character);
        if (el.isEmpty()) {
            LOG.warning(String.format("No element to resolve code lens at %s(%d,%d)", uri.getPath(), line, character));
            return "? references";
        }
        var ptr = new Ptr(el.get());
        // Update cache if necessary
        updateCacheCountReferences(uri);
        // Read reference count from cache
        var count = cacheCountReferences.getOrDefault(ptr, 0);

        if (count == 1) return "1 reference";
        return String.format("%d references", count);
    }

    @Override
    public List<TextEdit> formatting(DocumentFormattingParams params) {
        updateHoverCache(params.textDocument.uri, contents(params.textDocument.uri).content);

        var edits = new ArrayList<TextEdit>();
        edits.addAll(fixImports());
        edits.addAll(addOverrides());
        // TODO replace var with type name when vars are copy-pasted into fields
        return edits;
    }

    private List<TextEdit> fixImports() {
        // TODO if imports already match fixed-imports, return empty list
        // TODO preserve comments and other details of existing imports
        var imports = hoverCache.fixImports();
        var pos = hoverCache.sourcePositions();
        var lines = hoverCache.root.getLineMap();
        var edits = new ArrayList<TextEdit>();
        // Delete all existing imports
        for (var i : hoverCache.root.getImports()) {
            if (!i.isStatic()) {
                var offset = pos.getStartPosition(hoverCache.root, i);
                var line = (int) lines.getLineNumber(offset) - 1;
                var delete = new TextEdit(new Range(new Position(line, 0), new Position(line + 1, 0)), "");
                edits.add(delete);
            }
        }
        if (imports.isEmpty()) return edits;
        // Find a place to insert the new imports
        long insertLine = -1;
        var insertText = new StringBuilder();
        // If there are imports, use the start of the first import as the insert position
        for (var i : hoverCache.root.getImports()) {
            if (!i.isStatic() && insertLine == -1) {
                long offset = pos.getStartPosition(hoverCache.root, i);
                insertLine = lines.getLineNumber(offset) - 1;
            }
        }
        // If there are no imports, insert after the package declaration
        if (insertLine == -1 && hoverCache.root.getPackageName() != null) {
            long offset = pos.getEndPosition(hoverCache.root, hoverCache.root.getPackageName());
            insertLine = lines.getLineNumber(offset);
            insertText.append("\n");
        }
        // If there are no imports and no package, insert at the top of the file
        if (insertLine == -1) {
            insertLine = 0;
        }
        // Insert each import
        for (var i : imports) {
            insertText.append("import ").append(i).append(";\n");
        }
        var insertPosition = new Position((int) insertLine, 0);
        var insert = new TextEdit(new Range(insertPosition, insertPosition), insertText.toString());
        edits.add(insert);

        return edits;
    }

    private List<TextEdit> addOverrides() {
        var edits = new ArrayList<TextEdit>();
        var methods = hoverCache.needsOverrideAnnotation();
        var pos = hoverCache.sourcePositions();
        var lines = hoverCache.root.getLineMap();
        for (var t : methods) {
            var methodStart = pos.getStartPosition(t.getCompilationUnit(), t.getLeaf());
            var insertLine = lines.getLineNumber(methodStart);
            var indent = methodStart - lines.getPosition(insertLine, 0);
            var insertText = new StringBuilder();
            for (var i = 0; i < indent; i++) insertText.append(' ');
            insertText.append("@Override");
            insertText.append('\n');
            var insertPosition = new Position((int) insertLine - 1, 0);
            var insert = new TextEdit(new Range(insertPosition, insertPosition), insertText.toString());
            edits.add(insert);
        }
        return edits;
    }

    @Override
    public List<FoldingRange> foldingRange(FoldingRangeParams params) {
        updateCachedParse(params.textDocument.uri);
        var folds = cacheParse.foldingRanges();
        var all = new ArrayList<FoldingRange>();

        // Merge import ranges
        if (!folds.imports.isEmpty()) {
            var merged = asFoldingRange(folds.imports.get(0), FoldingRangeKind.Imports);
            for (var i : folds.imports) {
                var r = asFoldingRange(i, FoldingRangeKind.Imports);
                if (r.startLine <= merged.endLine + 1) {
                    merged =
                            new FoldingRange(
                                    merged.startLine,
                                    merged.startCharacter,
                                    r.endLine,
                                    r.endCharacter,
                                    FoldingRangeKind.Imports);
                } else {
                    all.add(merged);
                    merged = r;
                }
            }
            all.add(merged);
        }

        // Convert blocks and comments
        for (var t : folds.blocks) {
            all.add(asFoldingRange(t, FoldingRangeKind.Region));
        }
        for (var t : folds.comments) {
            all.add(asFoldingRange(t, FoldingRangeKind.Region));
        }

        return all;
    }

    private FoldingRange asFoldingRange(TreePath t, String kind) {
        var pos = cacheParse.sourcePositions();
        var lines = t.getCompilationUnit().getLineMap();
        var start = (int) pos.getStartPosition(t.getCompilationUnit(), t.getLeaf());
        var end = (int) pos.getEndPosition(t.getCompilationUnit(), t.getLeaf());

        // If this is a class tree, adjust start position to '{'
        if (t.getLeaf() instanceof ClassTree) {
            CharSequence content;
            try {
                content = t.getCompilationUnit().getSourceFile().getCharContent(true);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            for (var i = start; i < content.length(); i++) {
                if (content.charAt(i) == '{') {
                    start = i;
                    break;
                }
            }
        }

        // Convert offset to 0-based line and character
        var startLine = (int) lines.getLineNumber(start) - 1;
        var startChar = (int) lines.getColumnNumber(start) - 1;
        var endLine = (int) lines.getLineNumber(end) - 1;
        var endChar = (int) lines.getColumnNumber(end) - 1;

        // If this is a block, move end position back one line so we don't fold the '}'
        if (t.getLeaf() instanceof ClassTree || t.getLeaf() instanceof BlockTree) {
            endLine--;
        }

        return new FoldingRange(startLine, startChar, endLine, endChar, kind);
    }

    @Override
    public WorkspaceEdit rename(RenameParams params) {
        return null; // TODO
    }

    private boolean isJavaFile(URI uri) {
        return uri.getScheme().equals("file") && uri.getPath().endsWith(".java");
    }

    private List<URI> recentlyOpened = new ArrayList<>();

    @Override
    public void didOpenTextDocument(DidOpenTextDocumentParams params) {
        var document = params.textDocument;
        var uri = document.uri;
        if (!isJavaFile(uri)) return;
        LOG.info(String.format("Opened %s", Parser.fileName(uri)));
        activeDocuments.put(uri, new VersionedContent(document.text, document.version));
        recentlyOpened.add(uri);
        updateCachedParse(uri); // So that subsequent documentSymbol and codeLens requests will be faster
    }

    @Override
    public void didChangeTextDocument(DidChangeTextDocumentParams params) {
        var document = params.textDocument;
        var uri = document.uri;
        if (isJavaFile(uri)) {
            var existing = activeDocuments.get(uri);
            var newText = existing.content;

            if (document.version > existing.version) {
                for (var change : params.contentChanges) {
                    if (change.range == null) newText = change.text;
                    else newText = patch(newText, change);
                }

                activeDocuments.put(uri, new VersionedContent(newText, document.version));
            } else LOG.warning("Ignored change with version " + document.version + " <= " + existing.version);
        }
    }

    private String patch(String sourceText, TextDocumentContentChangeEvent change) {
        try {
            var range = change.range;
            var reader = new BufferedReader(new StringReader(sourceText));
            var writer = new StringWriter();

            // Skip unchanged lines
            int line = 0;

            while (line < range.start.line) {
                writer.write(reader.readLine() + '\n');
                line++;
            }

            // Skip unchanged chars
            for (int character = 0; character < range.start.character; character++) writer.write(reader.read());

            // Write replacement text
            writer.write(change.text);

            // Skip replaced text
            reader.skip(change.rangeLength);

            // Write remaining text
            while (true) {
                int next = reader.read();

                if (next == -1) return writer.toString();
                else writer.write(next);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void didCloseTextDocument(DidCloseTextDocumentParams params) {
        var document = params.textDocument;
        var uri = document.uri;
        if (isJavaFile(uri)) {
            // Remove from source cache
            activeDocuments.remove(uri);

            // Clear diagnostics
            publishDiagnostics(Collections.singletonList(uri), List.of());
        }
    }

    @Override
    public void didSaveTextDocument(DidSaveTextDocumentParams params) {
        var uri = params.textDocument.uri;
        if (isJavaFile(uri)) {
            // Re-lint all active documents
            lint(activeDocuments.keySet());
        }
    }

    Set<URI> activeDocuments() {
        return activeDocuments.keySet();
    }

    VersionedContent contents(URI openFile) {
        if (!isJavaFile(openFile)) {
            LOG.warning("Ignoring non-java file " + openFile);
            return VersionedContent.EMPTY;
        }
        if (activeDocuments.containsKey(openFile)) {
            return activeDocuments.get(openFile);
        }
        try {
            var content = Files.readAllLines(Paths.get(openFile)).stream().collect(Collectors.joining("\n"));
            return new VersionedContent(content, -1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
