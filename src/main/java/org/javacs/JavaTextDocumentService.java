package org.javacs;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.tree.MethodTree;
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
        lastCompletions.clear();
        var maybeCtx = server.compiler.parseFile(uri, content).completionPosition(line, column);
        if (!maybeCtx.isPresent()) {
            var items = new ArrayList<CompletionItem>();
            for (var name : CompileFocus.TOP_LEVEL_KEYWORDS) {
                var i = new CompletionItem();
                i.setLabel(name);
                i.setKind(CompletionItemKind.Keyword);
                i.setDetail("keyword");
                items.add(i);
            }
            var list = new CompletionList(true, items);
            return CompletableFuture.completedFuture(Either.forRight(list));
        }
        var ctx = maybeCtx.get();
        var focus = server.compiler.compileFocus(uri, content, ctx.line, ctx.character);
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
        var result = new ArrayList<CompletionItem>();
        for (var c : cs) {
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
        return CompletableFuture.completedFuture(Either.forRight(new CompletionList(isIncomplete, result)));
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
                var tree = server.compiler.docs().methodTree(method);
                var detail = tree.map(this::resolveDocDetail).orElse(resolveDefaultDetail(method));
                unresolved.setDetail(detail);

                var doc = server.compiler.docs().methodDoc(method);
                var markdown = doc.map(this::asMarkupContent);
                markdown.ifPresent(unresolved::setDocumentation);
            } else if (cached.element instanceof TypeElement) {
                var type = (TypeElement) cached.element;
                var doc = server.compiler.docs().classDoc(type);
                var markdown = doc.map(this::asMarkupContent);
                markdown.ifPresent(unresolved::setDocumentation);
            } else {
                LOG.info("Don't know how to look up docs for element " + cached.element);
            }
            // TODO constructors, fields
        } else if (cached.className != null) {
            var doc = server.compiler.docs().classDoc(cached.className.name);
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
            return server.compiler.docs().methodDoc(m).map(this::asMarkdown);
        } else if (e instanceof TypeElement) {
            var t = (TypeElement) e;
            return server.compiler.docs().classDoc(t).map(this::asMarkdown);
        } else return Optional.empty();
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
        var uri = URI.create(position.getTextDocument().getUri());
        var content = contents(uri).content;
        var line = position.getPosition().getLine() + 1;
        var column = position.getPosition().getCharacter() + 1;
        var e = server.compiler.compileFocus(uri, content, line, column).element();
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
        var doc = server.compiler.docs().methodDoc(e);
        var tree = server.compiler.docs().methodTree(e);
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
                        .compileFocus(uri, content, line, column)
                        .methodInvocation()
                        .map(this::asSignatureHelp)
                        .orElse(new SignatureHelp());
        return CompletableFuture.completedFuture(help);
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
        var fromUri = URI.create(position.getTextDocument().getUri());
        var fromLine = position.getPosition().getLine() + 1;
        var fromColumn = position.getPosition().getCharacter() + 1;
        var fromContent = contents(fromUri).content;
        var fromFocus = server.compiler.compileFocus(fromUri, fromContent, fromLine, fromColumn);
        var toEl = fromFocus.element();
        var toUri = fromFocus.declaringFile(toEl);
        if (!toUri.isPresent()) return CompletableFuture.completedFuture(List.of());
        var toContent = contents(toUri.get()).content;
        var toFile = server.compiler.compileFile(toUri.get(), toContent);
        var toPath = toFile.find(new Ptr(toEl));
        if (!toPath.isPresent()) return CompletableFuture.completedFuture(List.of());
        // Figure out where in the file the definition is
        var toRange = toFile.range(toPath.get());
        if (!toRange.isPresent()) return CompletableFuture.completedFuture(List.of());
        var to = new Location(toUri.get().toString(), toRange.get());
        return CompletableFuture.completedFuture(List.of(to));
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
        var toUri = URI.create(position.getTextDocument().getUri());
        var toContent = contents(toUri).content;
        var toLine = position.getPosition().getLine() + 1;
        var toColumn = position.getPosition().getCharacter() + 1;
        var toEl = server.compiler.compileFocus(toUri, toContent, toLine, toColumn).element();
        var fromFiles = server.compiler.potentialReferences(toEl);
        if (fromFiles.isEmpty()) return CompletableFuture.completedFuture(List.of());
        var batch = server.compiler.compileBatch(fromFiles);
        var fromTreePaths = batch.references(toEl);
        var result = new ArrayList<Location>();
        for (var path : fromTreePaths) {
            var fromUri = path.getCompilationUnit().getSourceFile().toUri();
            var fromRange = batch.range(path);
            if (!fromRange.isPresent()) {
                LOG.warning(String.format("Couldn't locate `%s`", path.getLeaf()));
                continue;
            }
            var from = new Location(fromUri.toString(), fromRange.get());
            result.add(from);
        }
        return CompletableFuture.completedFuture(result);
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

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        // TODO just create a blank code lens on every method, then resolve it async
        var uri = URI.create(params.getTextDocument().getUri());
        var content = contents(uri).content;
        var parse = server.compiler.parseFile(uri, content);
        var declarations = parse.declarations();
        var result = new ArrayList<CodeLens>();
        for (var d : declarations) {
            var range = parse.range(d);
            if (!range.isPresent()) continue;
            var className = JavaCompilerService.className(d);
            var memberName = JavaCompilerService.memberName(d);
            // If test class or method, add "Run Test" code lens
            if (parse.isTestClass(d)) {
                var command =
                        new Command("Run All Tests", "java.command.test.run", Arrays.asList(uri, className, null));
                var lens = new CodeLens(range.get(), command, null);
                result.add(lens);
                // TODO run all tests in file
                // TODO run all tests in package
            } else if (parse.isTestMethod(d)) {
                var command =
                        new Command(
                                "Run Test",
                                "java.command.test.run",
                                Arrays.asList(uri, className, memberName.orElse(null)));
                var lens = new CodeLens(range.get(), command, null);
                result.add(lens);
            }
            // If method or field, add an unresolved "_ references" code lens
            if (memberName.isPresent()) {
                var start = range.get().getStart();
                var line = start.getLine();
                var character = start.getCharacter();
                List<Object> data = List.of("java.command.findReferences", uri, line, character, new Ptr(d).toString());
                var lens = new CodeLens(range.get(), null, data);
                result.add(lens);
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    private Map<Ptr, Integer> cacheCountReferences = Collections.emptyMap();
    private URI cacheCountReferencesFile = URI.create("file:///NONE");

    private void updateCacheCountReferences(URI current) {
        if (cacheCountReferencesFile.equals(current)) return;
        LOG.info(String.format("Update cached reference count to %s...", current));
        var content = contents(current).content;
        cacheCountReferences = server.compiler.countReferences(current, content);
        cacheCountReferencesFile = current;
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        // Unpack data
        var data = (JsonArray) unresolved.getData();
        var command = data.get(0).getAsString();
        assert command.equals("java.command.findReferences");
        var uriString = data.get(1).getAsString();
        var line = data.get(2).getAsInt();
        var character = data.get(3).getAsInt();
        var ptrString = data.get(4).getAsString();
        // Parse data
        var uri = URI.create(uriString);
        var ptr = new Ptr(ptrString);
        // Update cache if necessary
        updateCacheCountReferences(uri);
        // Read reference count from cache
        var count = cacheCountReferences.getOrDefault(ptr, 0);
        // Update command
        String title;
        if (count == 1) title = "1 reference";
        else title = String.format("%d references", count);
        unresolved.setCommand(new Command(title, command, List.of(uri, line, character)));

        return CompletableFuture.completedFuture(unresolved);
    }

    private List<TextEdit> fixImports(URI java) {
        var contents = server.textDocuments.contents(java).content;
        var fix = server.compiler.compileFile(java, contents).fixImports();
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
