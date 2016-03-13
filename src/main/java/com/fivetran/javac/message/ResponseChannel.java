package com.fivetran.javac.message;

import java.io.IOException;

public interface ResponseChannel {
    void next(Response response) throws IOException;
}
