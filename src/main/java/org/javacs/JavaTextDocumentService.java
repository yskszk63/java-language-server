package org.javacs;

import com.google.gson.JsonPrimitive;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.lang.model.element.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

class JavaTextDocumentService implements TextDocumentService {
    private final JavaLanguageServer server;
    private final Map<URI, VersionedContent> activeDocuments = new HashMap<>();

    JavaTextDocumentService(JavaLanguageServer server) {
        this.server = server;
    }

    private CompletionItemKind completionItemKind(Element e) {
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

    /** Cache of completions from the last call to `completion` */
    private final Map<String, Completion> lastCompletions = new HashMap<>();

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        var uri = URI.create(position.getTextDocument().getUri());
        var content = contents(uri).content;
        var line = position.getPosition().getLine() + 1;
        var column = position.getPosition().getCharacter() + 1;
        var result = new ArrayList<CompletionItem>();
        lastCompletions.clear();
        var completions = server.compiler.completions(uri, content, line, column);
        for (var c : completions.items) {
            var i = new CompletionItem();
            var id = UUID.randomUUID().toString();
            i.setData(id);
            lastCompletions.put(id, c);
            if (c.element != null) {
                i.setLabel(c.element.getSimpleName().toString());
                i.setKind(completionItemKind(c.element));
                // Detailed name will be resolved later, using docs to fill in method names
                if (!(c.element instanceof ExecutableElement)) i.setDetail(c.element.toString());
                i.setSortText(2 + i.getLabel());
            } else if (c.packagePart != null) {
                i.setLabel(c.packagePart.name);
                i.setKind(CompletionItemKind.Module);
                i.setDetail(c.packagePart.fullName);
                i.setSortText(2 + i.getLabel());
            } else if (c.keyword != null) {
                i.setLabel(c.keyword);
                i.setKind(CompletionItemKind.Keyword);
                i.setDetail("keyword");
                i.setSortText(3 + i.getLabel());
            } else if (c.className != null) {
                i.setLabel(Parser.lastName(c.className.name));
                i.setKind(CompletionItemKind.Class);
                i.setDetail(c.className.name);
                if (c.className.isImported) i.setSortText(2 + i.getLabel());
                else i.setSortText(4 + i.getLabel());
            } else if (c.snippet != null) {
                i.setLabel(c.snippet.label);
                i.setKind(CompletionItemKind.Snippet);
                i.setInsertText(c.snippet.snippet);
                i.setInsertTextFormat(InsertTextFormat.Snippet);
                i.setSortText(1 + i.getLabel());
            } else throw new RuntimeException(c + " is not valid");

            result.add(i);
        }
        return CompletableFuture.completedFuture(Either.forRight(new CompletionList(completions.isIncomplete, result)));
    }

    private String resolveDocDetail(MethodTree doc) {
        var args = new StringJoiner(", ");
        for (var p : doc.getParameters()) {
            args.add(p.getName());
        }
        return String.format("%s %s(%s)", doc.getReturnType(), doc.getName(), args);
    }

    private String resolveDefaultDetail(ExecutableElement method) {
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
        content.setKind(MarkupKind.MARKDOWN);
        content.setValue(markdown);
        return content;
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        var idJson = (JsonPrimitive) unresolved.getData();
        var id = idJson.getAsString();
        var cached = lastCompletions.get(id);
        if (cached == null) {
            LOG.warning("CompletionItem " + id + " was not in the cache");
            return CompletableFuture.completedFuture(unresolved);
        }
        if (cached.element != null) {
            if (cached.element instanceof ExecutableElement) {
                var method = (ExecutableElement) cached.element;
                var tree = server.compiler.methodTree(method);
                var detail = tree.map(this::resolveDocDetail).orElse(resolveDefaultDetail(method));
                unresolved.setDetail(detail);

                var doc = server.compiler.methodDoc(method);
                var markdown = doc.map(this::asMarkupContent);
                markdown.ifPresent(unresolved::setDocumentation);
            } else if (cached.element instanceof TypeElement) {
                var type = (TypeElement) cached.element;
                var doc = server.compiler.classDoc(type);
                var markdown = doc.map(this::asMarkupContent);
                markdown.ifPresent(unresolved::setDocumentation);
            } else {
                LOG.info("Don't know how to look up docs for element " + cached.element);
            }
            // TODO constructors, fields
        } else if (cached.className != null) {
            var doc = server.compiler.classDoc(cached.className.name);
            var markdown = doc.map(this::asMarkupContent);
            markdown.ifPresent(unresolved::setDocumentation);
        }
        return CompletableFuture.completedFuture(unresolved);
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
        } else return e.toString();
    }

    private Optional<String> hoverDocs(Element e) {
        if (e instanceof ExecutableElement) {
            var m = (ExecutableElement) e;
            return server.compiler.methodDoc(m).map(this::asMarkdown);
        } else if (e instanceof TypeElement) {
            var t = (TypeElement) e;
            return server.compiler.classDoc(t).map(this::asMarkdown);
        } else return Optional.empty();
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
        var uri = URI.create(position.getTextDocument().getUri());
        var content = contents(uri).content;
        var line = position.getPosition().getLine() + 1;
        var column = position.getPosition().getCharacter() + 1;
        var e = server.compiler.element(uri, content, line, column);
        if (e != null) {
            List<Either<String, MarkedString>> result = new ArrayList<>();
            result.add(Either.forRight(new MarkedString("java.hover", hoverCode(e))));
            hoverDocs(e).ifPresent(doc -> result.add(Either.forLeft(doc)));
            return CompletableFuture.completedFuture(new Hover(result));
        } else return CompletableFuture.completedFuture(new Hover(Collections.emptyList()));
    }

    private List<ParameterInformation> signatureParamsFromDocs(MethodTree method, DocCommentTree doc) {
        var ps = new ArrayList<ParameterInformation>();
        var paramComments = new HashMap<String, String>();
        for (var tag : doc.getBlockTags()) {
            if (tag.getKind() == DocTree.Kind.PARAM) {
                var param = (ParamTree) tag;
                paramComments.put(param.getName().toString(), asMarkdown(param.getDescription()));
            }
        }
        for (var param : method.getParameters()) {
            var info = new ParameterInformation();
            var name = param.getName().toString();
            info.setLabel(name);
            if (paramComments.containsKey(name)) info.setDocumentation(paramComments.get(name));
            else info.setDocumentation(Objects.toString(param.getType(), ""));
            ps.add(info);
        }
        return ps;
    }

    private List<ParameterInformation> signatureParamsFromMethod(ExecutableElement e) {
        var missingParamNames = ShortTypePrinter.missingParamNames(e);
        var ps = new ArrayList<ParameterInformation>();
        for (var v : e.getParameters()) {
            var p = new ParameterInformation();
            if (missingParamNames) p.setLabel(ShortTypePrinter.print(v.asType()));
            else p.setLabel(v.getSimpleName().toString());
            ps.add(p);
        }
        return ps;
    }

    private SignatureInformation asSignatureInformation(ExecutableElement e) {
        var i = new SignatureInformation();
        var ps = signatureParamsFromMethod(e);
        var doc = server.compiler.methodDoc(e);
        var tree = server.compiler.methodTree(e);
        if (doc.isPresent() && tree.isPresent()) ps = signatureParamsFromDocs(tree.get(), doc.get());
        var args = ps.stream().map(p -> p.getLabel()).collect(Collectors.joining(", "));
        var name = e.getSimpleName().toString();
        if (name.equals("<init>")) name = e.getEnclosingElement().getSimpleName().toString();
        i.setLabel(name + "(" + args + ")");
        i.setParameters(ps);
        return i;
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
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        var uri = URI.create(position.getTextDocument().getUri());
        var content = contents(uri).content;
        var line = position.getPosition().getLine() + 1;
        var column = position.getPosition().getCharacter() + 1;
        var help =
                server.compiler
                        .methodInvocation(uri, content, line, column)
                        .map(this::asSignatureHelp)
                        .orElse(new SignatureHelp());
        return CompletableFuture.completedFuture(help);
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
        var uri = URI.create(position.getTextDocument().getUri());
        var line = position.getPosition().getLine() + 1;
        var column = position.getPosition().getCharacter() + 1;
        Function<URI, String> findContent = f -> contents(f).content;
        var def = server.compiler.definition(uri, line, column, findContent);
        if (!def.isPresent()) return CompletableFuture.completedFuture(List.of());
        var loc = asLocation(def.get());
        return CompletableFuture.completedFuture(List.of(loc));
    }

    class ReportProgress implements ReportReferencesProgress, AutoCloseable {
        private final Function<Integer, String> scanMessage, checkMessage;

        ReportProgress(
                String startMessage, Function<Integer, String> scanMessage, Function<Integer, String> checkMessage) {
            this.scanMessage = scanMessage;
            this.checkMessage = checkMessage;
            server.client().javaStartProgress(new JavaStartProgressParams(startMessage));
        }

        private int percent(int n, int d) {
            double nD = n, dD = d;
            double ratio = nD / dD;
            return (int) (ratio * 100);
        }

        public void scanForPotentialReferences(int nScanned, int nFiles) {
            var message = scanMessage.apply(nFiles);
            if (nScanned == 0) {
                server.client().javaReportProgress(new JavaReportProgressParams(message));
            } else {
                var increment = percent(nScanned, nFiles) > percent(nScanned - 1, nFiles) ? 1 : 0;
                server.client().javaReportProgress(new JavaReportProgressParams(message, increment));
            }
        }

        public void checkPotentialReferences(int nCompiled, int nPotential) {
            var message = checkMessage.apply(nCompiled);
            if (nCompiled == 0) {
                server.client().javaReportProgress(new JavaReportProgressParams(message));
            } else {
                var increment = percent(nCompiled, nPotential) > percent(nCompiled - 1, nPotential) ? 1 : 0;
                server.client().javaReportProgress(new JavaReportProgressParams(message, increment));
            }
        }

        @Override
        public void close() {
            server.client().javaEndProgress();
        }
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams position) {
        var uri = URI.create(position.getTextDocument().getUri());
        var content = contents(uri).content;
        var line = position.getPosition().getLine() + 1;
        var column = position.getPosition().getCharacter() + 1;
        var result = new ArrayList<Location>();
        var fileName = Paths.get(uri).getFileName();
        var startMessage = String.format("%s:%d", fileName, line);
        Function<Integer, String> scanMessage =
                nFiles -> String.format("Scan %,d files for potential references", nFiles);
        Function<Integer, String> checkMessage = nPotential -> String.format("Check %,d files", nPotential);
        try (var progress = new ReportProgress(startMessage, scanMessage, checkMessage)) {
            for (var r : server.compiler.references(uri, content, line, column, progress)) {
                result.add(asLocation(r));
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
        return null;
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        var uri = URI.create(params.getTextDocument().getUri());
        var content = contents(uri).content;
        var result =
                Parser.documentSymbols(Paths.get(uri), content)
                        .stream()
                        .map(Parser::asSymbolInformation)
                        .map(Either::<SymbolInformation, DocumentSymbol>forLeft)
                        .collect(Collectors.toList());
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return null;
    }

    private List<CodeLens> testMethods(URI uri) {
        var content = contents(uri).content;
        var tests = server.compiler.testMethods(uri, content);
        var result = new ArrayList<CodeLens>();
        for (var test : tests) {
            var maybePos = server.compiler.position(uri, content, test);
            if (!maybePos.isPresent()) {
                LOG.warning(String.format("No position for %s", test));
                continue;
            }
            var pos = maybePos.get();
            var range = asRange(pos);
            var message = pos.memberName.isPresent() ? "Run Test" : "Run All Tests";
            var command =
                    new Command(
                            message,
                            "java.command.test.run",
                            Arrays.asList(uri, pos.className.orElse(null), pos.memberName.orElse(null)));
            var lens = new CodeLens(range, command, null);
            result.add(lens);
        }
        // TODO run all tests in file
        // TODO run all tests in package
        return result;
    }

    private List<CodeLens> allReferences(URI uri) {
        var content = contents(uri).content;
        var fileName = Paths.get(uri).getFileName();
        var startMessage = String.format("%s", fileName);
        Function<Integer, String> scanMessage = nFiles -> String.format("Scan %,d files for imports", nFiles);
        Function<Integer, String> checkMessage = nPotential -> String.format("Index %,d files", nPotential);
        try (var progress = new ReportProgress(startMessage, scanMessage, checkMessage)) {
            // Organize by method
            var refs = server.compiler.referencesFile(uri, content, progress);
            var byId = new HashMap<String, List<TreePath>>();
            for (var r : refs) {
                byId.computeIfAbsent(r.toEl, __ -> new ArrayList<>()).add(r);
            }
            // Convert into CodeLens messages
            var result = new ArrayList<CodeLens>();
            for (var kv : byId.entrySet()) {
                // Figure out command
                var id = kv.getKey();
                var rs = kv.getValue();
                // Figure out where to place command
                var r = rs.get(0);
                var pos = server.compiler.position(uri, content, r.toEl);
                if (!pos.isPresent()) {
                    LOG.warning(String.format("No position for %s", r.toEl));
                    continue;
                }
                var range = asRange(pos.get());
                String message;
                if (rs.isEmpty()) message = "0 references";
                else if (rs.size() == 1) message = "1 reference";
                else message = String.format("%d references", rs.size());
                var command =
                        new Command(
                                message,
                                "java.command.findReferences",
                                List.of(uri, range.getStart().getLine(), range.getStart().getCharacter()));
                var lens = new CodeLens(range, command, null);
                result.add(lens);
            }
            return result;
        }
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        // TODO just create a blank code lens on every method, then resolve it async
        var uri = URI.create(params.getTextDocument().getUri());
        var result = new ArrayList<CodeLens>();
        result.addAll(testMethods(uri));
        result.addAll(allReferences(uri));
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        return null;
    }

    private List<TextEdit> fixImports(URI java) {
        var contents = server.textDocuments.contents(java).content;
        var fix = server.compiler.fixImports(java, contents);
        // TODO if imports already match fixed-imports, return empty list
        // TODO preserve comments and other details of existing imports
        var edits = new ArrayList<TextEdit>();
        // Delete all existing imports
        for (var i : fix.parsed.getImports()) {
            if (!i.isStatic()) {
                var offset = fix.sourcePositions.getStartPosition(fix.parsed, i);
                var line = (int) fix.parsed.getLineMap().getLineNumber(offset) - 1;
                var delete = new TextEdit(new Range(new Position(line, 0), new Position(line + 1, 0)), "");
                edits.add(delete);
            }
        }
        if (fix.fixedImports.isEmpty()) return edits;
        // Find a place to insert the new imports
        long insertLine = -1;
        var insertText = new StringBuilder();
        // If there are imports, use the start of the first import as the insert position
        for (var i : fix.parsed.getImports()) {
            if (!i.isStatic() && insertLine == -1) {
                long offset = fix.sourcePositions.getStartPosition(fix.parsed, i);
                insertLine = fix.parsed.getLineMap().getLineNumber(offset) - 1;
            }
        }
        // If there are no imports, insert after the package declaration
        if (insertLine == -1 && fix.parsed.getPackageName() != null) {
            long offset = fix.sourcePositions.getEndPosition(fix.parsed, fix.parsed.getPackageName());
            insertLine = fix.parsed.getLineMap().getLineNumber(offset);
            insertText.append("\n");
        }
        // If there are no imports and no package, insert at the top of the file
        if (insertLine == -1) {
            insertLine = 0;
        }
        // Insert each import
        fix.fixedImports
                .stream()
                .sorted()
                .forEach(
                        i -> {
                            insertText.append("import ").append(i).append(";\n");
                        });
        var insertPosition = new Position((int) insertLine, 0);
        var insert = new TextEdit(new Range(insertPosition, insertPosition), insertText.toString());
        edits.add(insert);
        return edits;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        var uri = URI.create(params.getTextDocument().getUri());
        return CompletableFuture.completedFuture(fixImports(uri));
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
        return null;
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return null;
    }

    private boolean isJava(URI uri) {
        return uri.getPath().endsWith(".java");
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var document = params.getTextDocument();
        var uri = URI.create(document.getUri());
        if (isJava(uri)) {
            activeDocuments.put(uri, new VersionedContent(document.getText(), document.getVersion()));
            server.lint(Collections.singleton(uri));
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var document = params.getTextDocument();
        var uri = URI.create(document.getUri());
        if (isJava(uri)) {
            var existing = activeDocuments.get(uri);
            var newText = existing.content;

            if (document.getVersion() > existing.version) {
                for (var change : params.getContentChanges()) {
                    if (change.getRange() == null) newText = change.getText();
                    else newText = patch(newText, change);
                }

                activeDocuments.put(uri, new VersionedContent(newText, document.getVersion()));
            } else LOG.warning("Ignored change with version " + document.getVersion() + " <= " + existing.version);
        }
    }

    private String patch(String sourceText, TextDocumentContentChangeEvent change) {
        try {
            var range = change.getRange();
            var reader = new BufferedReader(new StringReader(sourceText));
            var writer = new StringWriter();

            // Skip unchanged lines
            int line = 0;

            while (line < range.getStart().getLine()) {
                writer.write(reader.readLine() + '\n');
                line++;
            }

            // Skip unchanged chars
            for (int character = 0; character < range.getStart().getCharacter(); character++)
                writer.write(reader.read());

            // Write replacement text
            writer.write(change.getText());

            // Skip replaced text
            reader.skip(change.getRangeLength());

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
    public void didClose(DidCloseTextDocumentParams params) {
        var document = params.getTextDocument();
        var uri = URI.create(document.getUri());
        if (isJava(uri)) {
            // Remove from source cache
            activeDocuments.remove(uri);

            // Clear diagnostics
            server.publishDiagnostics(Collections.singletonList(uri), Collections.emptyList());
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        var uri = URI.create(params.getTextDocument().getUri());
        if (isJava(uri)) {
            // Re-lint all active documents
            server.lint(activeDocuments.keySet());
            // TODO update config when java file implies a new source root
        }
        // TODO update config when pom.xml changes
    }

    Set<URI> activeDocuments() {
        return activeDocuments.keySet();
    }

    VersionedContent contents(URI openFile) {
        if (activeDocuments.containsKey(openFile)) {
            return activeDocuments.get(openFile);
        } else {
            try {
                var content = Files.readAllLines(Paths.get(openFile)).stream().collect(Collectors.joining("\n"));
                return new VersionedContent(content, -1);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final Logger LOG = Logger.getLogger("main");
}
