package org.javacs;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

public interface CustomLanguageClient extends LanguageClient {

    @JsonNotification("java/startProgress")
    void javaStartProgress(JavaStartProgressParams params);

    @JsonNotification("java/reportProgress")
    void javaReportProgress(JavaReportProgressParams params);

    @JsonNotification("java/endProgress")
    void javaEndProgress();
}
