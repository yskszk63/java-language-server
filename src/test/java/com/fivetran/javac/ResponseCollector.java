package com.fivetran.javac;

import com.fivetran.javac.message.Response;
import com.fivetran.javac.message.ResponseChannel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ResponseCollector implements ResponseChannel {
    public final List<Response> responses = new ArrayList<>();

    @Override
    public void next(Response response) throws IOException {
        responses.add(response);
    }
}
