package org.javacs;

import io.typefox.lsapi.MessageParams;
import io.typefox.lsapi.MessageParamsImpl;

public class ShowMessageException extends RuntimeException {
    private final MessageParams message;

    public ShowMessageException(MessageParams message, Exception cause) {
        super(message.getMessage(), cause);

        this.message = message;
    }

    public static ShowMessageException error(String message, Exception cause) {
        MessageParamsImpl m = new MessageParamsImpl();

        m.setMessage(message);
        m.setType(MessageParams.TYPE_ERROR);

        return new ShowMessageException(m, cause);
    }
}
