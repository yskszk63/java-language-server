package org.javacs;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.MappingIterator;
import org.javacs.message.*;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
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

        System.setProperty("javacs.sourcePath", "src/test/resources");
        System.setProperty("javacs.classPath", "");
        System.setProperty("javacs.outputDirectory", "target");

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
        Range range = new Range(new Position(2, 15), new Position(2, 18));
        responseLint.messages.put(file.toString(),
                                  Collections.singletonList(new LintMessage(range, message, LintMessage.Type.Error)));

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
        Range range = new Range(new Position(0, 71), new Position(0, 74));
        responseLint.messages.put(file.toString(),
                                  Collections.singletonList(new LintMessage(range, message, LintMessage.Type.Error)));

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

        assertThat(responses.toString(), containsString("class NotJava is public, should be declared in a file named NotJava.java"));
    }

    @Test
    public void lintNoSuchFile() throws URISyntaxException, IOException {
        String request = "{\"requestId\":1,\"lint\":{\"path\":\"/NoSuchFile.java\"}}";

        List<Response> responses = responses(request.toString());

        assertThat(responses.toString(), containsString("No such file or directory"));
    }

    @Test
    public void badlyFormattedRequest() throws IOException {
        String request = "{\"requestId\":1,\"lint\":[\"oops!\"]}";

        List<Response> responses = responses(request.toString());

        assertThat(responses, not(empty()));
    }
}
