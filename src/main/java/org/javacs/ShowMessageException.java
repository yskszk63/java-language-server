package org.javacs;

import io.typefox.lsapi.MessageParams;
import io.typefox.lsapi.MessageType;
import io.typefox.lsapi.impl.MessageParamsImpl;

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
        MessageParamsImpl m = new MessageParamsImpl();

        m.setMessage(message);
        m.setType(type);

        return new ShowMessageException(m, cause);
    }
}
