package org.javacs;

import io.typefox.lsapi.*;

import java.util.List;
import java.util.logging.Logger;

class JavaLanguageServer implements LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");
    private String workspaceRoot;
    private NotificationCallback<PublishDiagnosticsParams> publishDiagnostics = p -> {};
    private NotificationCallback<MessageParams> showMessage = m -> {};

    @Override
    public InitializeResult initialize(InitializeParams params) {
        workspaceRoot = params.getRootPath();

        InitializeResultImpl result = new InitializeResultImpl();

        result.setCapabilities(new ServerCapabilitiesImpl());

        ServerCapabilitiesImpl capabilities = result.getCapabilities();

        // TODO incremental mode
        capabilities.setTextDocumentSync(ServerCapabilities.SYNC_FULL);
        capabilities.setDefinitionProvider(true);

        return result;
    }

    @Override
    public void shutdown() {

    }

    @Override
    public void exit() {

    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return new TextDocumentService() {
            @Override
            public List<? extends CompletionItem> completion(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public CompletionItem resolveCompletionItem(CompletionItem unresolved) {
                return null;
            }

            @Override
            public Hover hover(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public SignatureHelp signatureHelp(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public List<? extends Location> definition(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public List<? extends Location> references(ReferenceParams params) {
                return null;
            }

            @Override
            public DocumentHighlight documentHighlight(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public List<? extends SymbolInformation> documentSymbol(DocumentSymbolParams params) {
                return null;
            }

            @Override
            public List<? extends Command> codeAction(CodeActionParams params) {
                return null;
            }

            @Override
            public List<? extends CodeLens> codeLens(CodeLensParams params) {
                return null;
            }

            @Override
            public CodeLens resolveCodeLens(CodeLens unresolved) {
                return null;
            }

            @Override
            public List<? extends TextEdit> formatting(DocumentFormattingParams params) {
                return null;
            }

            @Override
            public List<? extends TextEdit> rangeFormatting(DocumentRangeFormattingParams params) {
                return null;
            }

            @Override
            public List<? extends TextEdit> onTypeFormatting(DocumentOnTypeFormattingParams params) {
                return null;
            }

            @Override
            public WorkspaceEdit rename(RenameParams params) {
                return null;
            }

            @Override
            public void didOpen(DidOpenTextDocumentParams params) {
                String text = params.getTextDocument().getText();

                LOG.info("Show open message");

                MessageParamsImpl message = new MessageParamsImpl();

                message.setType(MessageParams.TYPE_INFO);
                message.setMessage("Opened " + params.getUri());

                showMessage.call(message);
            }

            @Override
            public void didChange(DidChangeTextDocumentParams params) {
                // TODO
                String text = params.getContentChanges().get(0).getText();
            }

            @Override
            public void didClose(DidCloseTextDocumentParams params) {

            }

            @Override
            public void didSave(DidSaveTextDocumentParams params) {

            }

            @Override
            public void onPublishDiagnostics(NotificationCallback<PublishDiagnosticsParams> callback) {
                publishDiagnostics = callback;
            }
        };
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new WorkspaceService() {
            @Override
            public List<? extends SymbolInformation> symbol(WorkspaceSymbolParams params) {
                return null;
            }

            @Override
            public void didChangeConfiguraton(DidChangeConfigurationParams params) {

            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {

            }
        };
    }

    @Override
    public WindowService getWindowService() {
        return new WindowService() {
            @Override
            public void onShowMessage(NotificationCallback<MessageParams> callback) {
                showMessage = callback;
            }

            @Override
            public void onShowMessageRequest(NotificationCallback<ShowMessageRequestParams> callback) {

            }

            @Override
            public void onLogMessage(NotificationCallback<MessageParams> callback) {

            }
        };
    }
}
