package org.javacs;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.lsp4j.launch.LSPLauncher;

public class Main {
    public static final ObjectMapper JSON =
            new ObjectMapper()
                    .registerModule(new Jdk8Module())
                    .registerModule(new JSR310Module())
                    .registerModule(pathAsJson())
                    .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

    private static final Logger LOG = Logger.getLogger("main");

    public static void setRootFormat() {
        var root = Logger.getLogger("");

        for (var h : root.getHandlers()) h.setFormatter(new LogFormat());
    }

    private static SimpleModule pathAsJson() {
        var m = new SimpleModule();

        m.addSerializer(
                Path.class,
                new JsonSerializer<Path>() {
                    @Override
                    public void serialize(Path path, JsonGenerator gen, SerializerProvider serializerProvider)
                            throws IOException, JsonProcessingException {
                        gen.writeString(path.toString());
                    }
                });

        m.addDeserializer(
                Path.class,
                new JsonDeserializer<Path>() {
                    @Override
                    public Path deserialize(JsonParser parse, DeserializationContext deserializationContext)
                            throws IOException, JsonProcessingException {
                        return Paths.get(parse.getText());
                    }
                });

        return m;
    }

    public static void main(String[] args) {
        try {
            // Logger.getLogger("").addHandler(new FileHandler("javacs.%u.log", false));
            setRootFormat();

            var server = new JavaLanguageServer();
            var threads = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "client"));
            var launcher =
                    LSPLauncher.createServerLauncher(
                            server, System.in, System.out, threads, messageConsumer -> messageConsumer);

            server.installClient(launcher.getRemoteProxy());
            launcher.startListening();
            LOG.info(String.format("java.version is %s", System.getProperty("java.version")));
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);

            System.exit(1);
        }
    }
}
