package com.fivetran.javac;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.fivetran.javac.message.Request;
import com.fivetran.javac.message.Response;
import com.fivetran.javac.message.ResponseChannel;
import com.fivetran.javac.message.ResponseError;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;
import static java.util.stream.Collectors.toList;

public class Main {
    public static final ObjectMapper JSON = new ObjectMapper().registerModule(new Jdk8Module())
                                                              .registerModule(new JSR310Module())
                                                              .registerModule(pathAsJson())
                                                              .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

    private static SimpleModule pathAsJson() {
        SimpleModule m = new SimpleModule();

        m.addSerializer(Path.class, new JsonSerializer<Path>() {
            @Override
            public void serialize(Path path,
                                  JsonGenerator gen,
                                  SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
                gen.writeString(path.toString());
            }
        });

        m.addDeserializer(Path.class, new JsonDeserializer<Path>() {
            @Override
            public Path deserialize(JsonParser parse,
                                    DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
                return Paths.get(parse.getText());
            }
        });

        return m;
    }

    private static final ObjectMapper PRETTY_JSON = new ObjectMapper().registerModule(new Jdk8Module())
                                                                      .registerModule(new JSR310Module())
                                                                      .registerModule(pathAsJson())
                                                                      .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
                                                                      .configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);

    private static final Logger LOG = Logger.getLogger("");

    public static void main(String[] args) throws IOException {
        LoggingFormat.startLogging();

        Connection connection = connectToNode();
        JsonParser parser = JSON.getFactory().createParser(connection.in);
        MappingIterator<Request> requests = JSON.readValues(parser, Request.class);

        ResponseChannel responses = response -> {
            JSON.writeValue(connection.out, response);

            connection.out.print('\n');
            connection.out.flush();
        };

        new Main(requests, responses).run();
    }

    private static Connection connectToNode() throws IOException {
        String port = System.getProperty("javac-services.port");

        if (port != null) {
            Socket socket = new Socket("localhost", Integer.parseInt(port));

            InputStream in = socket.getInputStream();
            PrintStream out = new PrintStream(socket.getOutputStream());

            LOG.info("Connected to parent using socket on port " + port);

            return new Connection(in, out);
        }
        else {
            InputStream in = System.in;
            PrintStream out = System.out;

            LOG.info("Connected to parent using stdio");

            return new Connection(in, out);
        }
    }

    private static class Connection {
        final InputStream in;
        final PrintStream out;

        private Connection(InputStream in, PrintStream out) {
            this.in = in;
            this.out = out;
        }
    }

    public Main(MappingIterator<Request> in, ResponseChannel out) {
        this.in = in;
        this.out = out;
        this.pool = new ScheduledThreadPoolExecutor(8);
    }

    /**
     * Requests from the parent node process
     */
    public final MappingIterator<Request> in;

    /**
     * Where to send the responses
     */
    public final ResponseChannel out;

    /**
     * Thread pool that gets used to execute requests
     */
    // TODO java compiler is shared, consider removing threads
    private final ScheduledThreadPoolExecutor pool;

    private final JavacHolder compiler = systemPropsCompiler();

    private final Services services = new Services(compiler);

    /**
     * Listen for requests from the parent node process.
     * Send replies asynchronously.
     * When the request stream is closed, wait for 5s for all outstanding responses to compute, then return.
     */
    public void run() throws IOException {
        try {
            while (in.hasNextValue()) {
                final Request request = in.nextValue();

                pool.submit(() -> handleRequest(request));
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error reading request", e);

            Response response = new Response();

            response.error = Optional.of(new ResponseError(e.getMessage()));

            out.next(response);
        }

        pool.shutdown();

        try {
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private void handleRequest(Request request) {
        Response response = new Response(request.requestId);

        try {
            // Put request id in logging context
            LoggingFormat.request.set(request.requestId);

            LOG.info("request " + prettyPrint(request));

            if (request.echo.isPresent())
                response.echo = Optional.of(services.echo(request.echo.get()));
            else if (request.lint.isPresent())
                response.lint = Optional.of(services.lint(request.lint.get()));
            else if (request.autocomplete.isPresent())
                response.autocomplete = Optional.of(services.autocomplete(request.autocomplete.get()));
            else if (request.requestGoto.isPresent())
                response.responseGoto = Optional.of(services.doGoto(request.requestGoto.get()));
                // Continue the pattern for additional request / response types
            else
                LOG.severe("Unrecognized message " + request);
        } catch (ReturnError error) {
            response.error = Optional.of(new ResponseError(error.message));
        } catch (Throwable e) {
            response.error = Optional.of(new ResponseError(e.getClass().getSimpleName() + ": " + e.getMessage()));

            LOG.log(Level.SEVERE, e.getMessage(), e);
        }

        try {
            LOG.info("response " + prettyPrint(response));

            out.next(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String prettyPrint(Object value) throws JsonProcessingException {
        Map asMap = PRETTY_JSON.convertValue(value, Map.class);

        return PRETTY_JSON.writeValueAsString(asMap);
    }

    private JavacHolder systemPropsCompiler() {
        String sourcePathString = System.getProperty("javac-services.sourcePath");
        String classPathString = System.getProperty("javac-services.classPath");
        String outputDirectoryString = System.getProperty("javac-services.outputDirectory");

        Objects.requireNonNull(sourcePathString, "You must specify -Djavac-services.sourcePath as a command-line argument");
        Objects.requireNonNull(classPathString, "You must specify -Djavac-services.classPath as a command-line argument");
        Objects.requireNonNull(outputDirectoryString, "You must specify -Djavac-services.outputDirectory as a command-line argument");

        List<Path> sourcePath = Arrays.stream(sourcePathString.split(":")).map(Paths::get).collect(toList());
        List<Path> classPath = Arrays.stream(classPathString.split(":")).map(Paths::get).collect(toList());
        Path outputDirectory = Paths.get(outputDirectoryString);

        return new JavacHolder(classPath,
                               sourcePath,
                               outputDirectory);
    }
}
