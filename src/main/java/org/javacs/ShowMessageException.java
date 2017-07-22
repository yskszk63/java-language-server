package org.javacs;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

public class ShowMessageException extends RuntimeException {
    public final MessageParams message;

    public ShowMessageException(MessageParams message, Exception cause) {
        super(message.getMessage(), cause);

        this.message = message;
    }

    public static ShowMessageException error(String message, Exception cause) {
        return create(MessageType.Error, message, cause);
    }

    public static ShowMessageException warning(String message, Exception cause) {
        return create(MessageType.Warning, message, cause);
    }

    private static ShowMessageException create(MessageType type, String message, Exception cause) {
        MessageParams m = new MessageParams(type, message);

        return new ShowMessageException(m, cause);
    }
}
