package org.javacs;

import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.lsp4j.launch.LSPLauncher;

public class Main {
    private static final Logger LOG = Logger.getLogger("main");

    public static void setRootFormat() {
        var root = Logger.getLogger("");

        for (var h : root.getHandlers()) h.setFormatter(new LogFormat());
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
