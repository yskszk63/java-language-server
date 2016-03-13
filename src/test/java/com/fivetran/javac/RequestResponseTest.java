package com.fivetran.javac;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fivetran.javac.message.*;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

public class RequestResponseTest extends Fixtures {

    @Test
    public void echo() throws IOException {
        String request = "{\"requestId\":1,\"echo\":\"Hello world!\"}";
        List<Response> responses = responses(request);

        Response expected = new Response(1);

        expected.echo = Optional.of(Main.JSON.readTree("\"Hello world!\""));

        assertThat(responses, hasItem(expected));

        request = "{\"requestId\":2,\"echo\":\"Hello world!\"}";
        responses = responses(request);

        expected = new Response(2);

        expected.echo = Optional.of(Main.JSON.readTree("\"Hello world!\""));

        assertThat(responses, hasItem(expected));
    }

    private List<Response> responses(String request) throws IOException {
        JsonParser parser = Main.JSON.getFactory().createParser(request);
        MappingIterator<Request> in = Main.JSON.readValues(parser, Request.class);
        ResponseCollector out = new ResponseCollector();

        new Main(in, out).run();

        return out.responses;
    }

    @Test
    public void lintUndefinedSymbol() throws URISyntaxException, IOException {
        Path file = Paths.get(RequestResponseTest.class.getResource("/UndefinedSymbol.java").toURI());

        RequestLint lint = new RequestLint();
        lint.path = file.toString();

        Request request = new Request();
        request.requestId = 2;
        request.lint = Optional.of(lint);

        List<Response> responses = responses(request.toString());

        ResponseLint responseLint = new ResponseLint();
        String message = "cannot find symbol\n  symbol:   variable foo\n  location: class UndefinedSymbol";
        Range range = new Range(new Point(2, 15), new Point(2, 18));
        responseLint.messages.add(new LintMessage(LintMessage.Type.Error, message, lint.path, range));

        Response expected = new Response(request.requestId);
        expected.lint = Optional.of(responseLint);

        assertThat(responses, hasItem(expected));
    }

    @Test
    public void lintSingleLineUndefinedSymbol() throws URISyntaxException, IOException {
        Path file = Paths.get(RequestResponseTest.class.getResource("/SingleLineUndefinedSymbol.java").toURI());

        RequestLint lint = new RequestLint();
        lint.path = file.toString();

        Request request = new Request();
        request.requestId = 2;
        request.lint = Optional.of(lint);

        List<Response> responses = responses(request.toString());

        ResponseLint responseLint = new ResponseLint();
        String message = "cannot find symbol\n  symbol:   variable foo\n  location: class SingleLineUndefinedSymbol";
        Range range = new Range(new Point(0, 71), new Point(0, 74));
        responseLint.messages.add(new LintMessage(LintMessage.Type.Error, message, lint.path, range));

        Response expected = new Response(request.requestId);
        expected.lint = Optional.of(responseLint);

        assertThat(responses, hasItem(expected));
    }

    @Test
    public void lintNotJava() throws URISyntaxException, IOException {
        Path file = Paths.get(RequestResponseTest.class.getResource("/NotJava.java.txt").toURI());

        RequestLint lint = new RequestLint();
        lint.path = file.toString();

        Request request = new Request();
        request.requestId = 2;
        request.lint = Optional.of(lint);

        List<Response> responses = responses(request.toString());

        assertThat(responses.toString(), containsString("Compilation unit is not of SOURCE kind"));
    }

    @Test
    public void lintNoSuchFile() throws URISyntaxException, IOException {
        String request = "{\"requestId\":1,\"lint\":{\"path\":\"/NoSuchFile.java\"}}";

        List<Response> responses = responses(request.toString());

        assertThat(responses.toString(), containsString("NoSuchFileException: /NoSuchFile.java"));
    }
}
