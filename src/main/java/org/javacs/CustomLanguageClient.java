package org.javacs;

import org.javacs.lsp.*;

public interface CustomLanguageClient extends LanguageClient {

    @JsonNotification("java/startProgress")
    void javaStartProgress(JavaStartProgressParams params);

    @JsonNotification("java/reportProgress")
    void javaReportProgress(JavaReportProgressParams params);

    @JsonNotification("java/endProgress")
    void javaEndProgress();
}
