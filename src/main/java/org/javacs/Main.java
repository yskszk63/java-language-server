package org.javacs;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import io.typefox.lsapi.Message;
import io.typefox.lsapi.services.json.LanguageServerToJsonAdapter;
import io.typefox.lsapi.services.json.MessageJsonHandler;
import io.typefox.lsapi.services.json.StreamMessageReader;
import io.typefox.lsapi.services.json.StreamMessageWriter;
import io.typefox.lsapi.services.launch.LanguageServerLauncher;
import io.typefox.lsapi.services.transport.io.ConcurrentMessageReader;
import io.typefox.lsapi.services.transport.io.MessageReader;
import io.typefox.lsapi.services.transport.server.LanguageServerEndpoint;
import io.typefox.lsapi.services.transport.trace.MessageTracer;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOG = Logger.getLogger("main");
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

    public static void main(String[] args) throws IOException {
        try {
            LoggingFormat.startLogging();

            Connection connection = connectToNode();

            run(connection);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);

            System.exit(1);
        }
    }

    private static Connection connectToNode() throws IOException {
        String port = System.getProperty("javacs.port");

        if (port != null) {
            Socket socket = new Socket("localhost", Integer.parseInt(port));

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            OutputStream intercept = new OutputStream() {

                @Override
                public void write(int b) throws IOException {
                    out.write(b);
                }
            };

            LOG.info("Connected to parent using socket on port " + port);

            return new Connection(in, intercept);
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
        final OutputStream out;

        private Connection(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }
    }

    /**
     * Listen for requests from the parent node process.
     * Send replies asynchronously.
     * When the request stream is closed, wait for 5s for all outstanding responses to compute, then return.
     */
    public static void run(Connection connection) {
        MessageJsonHandler handler = new MessageJsonHandler();
        StreamMessageReader reader = new StreamMessageReader(connection.in, handler);
        StreamMessageWriter writer = new StreamMessageWriter(connection.out, handler);
        JavaLanguageServer server = new JavaLanguageServer();
        LanguageServerEndpoint endpoint = new LanguageServerEndpoint(server);

        endpoint.setMessageTracer(new MessageTracer() {
            @Override
            public void onError(String message, Throwable err) {
                LOG.log(Level.SEVERE, message, err);
            }

            @Override
            public void onRead(Message message, String s) {

            }

            @Override
            public void onWrite(Message message, String s) {

            }
        });

        reader.setOnError(err -> LOG.log(Level.SEVERE, err.getMessage(), err));
        writer.setOnError(err -> LOG.log(Level.SEVERE, err.getMessage(), err));

        endpoint.connect(reader, writer);

        LOG.info("Connection closed");
    }
}
