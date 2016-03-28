package org.javacs.message;

import java.io.IOException;

public interface ResponseChannel {
    void next(Response response) throws IOException;
}
