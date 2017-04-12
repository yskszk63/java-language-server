package org.javacs;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.javacs.Main.JSON;

class FindConfig {
    private final Path workspaceRoot;
    
    /**
     * Instead of looking for javaconfig.json, just use this one.
     * For testing.
     */
    private final Optional<JavacConfig> testConfig;

    // TODO invalidate cache when VSCode notifies us config file has changed
    private final Map<Path, Optional<JavacConfig>> configCache = new HashMap<>();

    FindConfig(Path workspaceRoot, Optional<JavacConfig> testConfig) {
        this.workspaceRoot = workspaceRoot;
        this.testConfig = testConfig;
    }

    Optional<JavacConfig> forFile(Path file) {
        if (!file.toFile().isDirectory())
            file = file.getParent();

        if (file == null)
            return Optional.empty();

        return configCache.computeIfAbsent(file, this::doFindConfig);
    }

    private Optional<JavacConfig> doFindConfig(Path dir) {
        if (testConfig.isPresent())
            return testConfig;

        while (true) {
            Optional<JavacConfig> found = readIfConfig(dir);

            if (found.isPresent())
                return found;
            else if (workspaceRoot.startsWith(dir))
                return Optional.empty();
            else
                dir = dir.getParent();
        }
    }

    /**
     * If directory contains a config file, for example javaconfig.json or an eclipse project file, read it.
     */
    private Optional<JavacConfig> readIfConfig(Path dir) {
        if (Files.exists(dir.resolve("javaconfig.json"))) {
            JavaConfigJson json = readJavaConfigJson(dir.resolve("javaconfig.json"));
            Set<Path> classPath = json.classPathFile.map(classPathFile -> {
                Path classPathFilePath = dir.resolve(classPathFile);
                return readClassPathFile(classPathFilePath);
            }).orElse(Collections.emptySet());
            Set<Path> sourcePath = json.sourcePath.stream().map(dir::resolve).collect(Collectors.toSet());
            Path outputDirectory = dir.resolve(json.outputDirectory);
            JavacConfig config = new JavacConfig(sourcePath, classPath, outputDirectory);

            return Optional.of(config);
        }
        else if (Files.exists(dir.resolve("pom.xml"))) {
            Path pomXml = dir.resolve("pom.xml");

            // Invoke maven to get classpath
            Set<Path> classPath = buildClassPath(pomXml);

            // Get source directory from pom.xml
            Set<Path> sourcePath = sourceDirectories(pomXml);

            // Use target/javacs
            Path outputDirectory = Paths.get("target/classes").toAbsolutePath();

            JavacConfig config = new JavacConfig(sourcePath, classPath, outputDirectory);

            return Optional.of(config);
        }
        // TODO add more file types
        else {
            return Optional.empty();
        }
    }

    public static Set<Path> buildClassPath(Path pomXml) {
        try {
            Objects.requireNonNull(pomXml, "pom.xml path is null");

            // Tell maven to output classpath to a temporary file
            // TODO if pom.xml already specifies outputFile, use that location
            Path classPathTxt = Files.createTempFile("classpath", ".txt");

            LOG.info("Emit classpath to " + classPathTxt);

            String cmd = getMvnCommand() + " dependency:build-classpath -Dmdep.outputFile=" + classPathTxt;
            File workingDirectory = pomXml.toAbsolutePath().getParent().toFile();
            int result = Runtime.getRuntime().exec(cmd, null, workingDirectory).waitFor();

            if (result != 0)
                throw new RuntimeException("`" + cmd + "` returned " + result);

            return readClassPathFile(classPathTxt);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getMvnCommand() {
        String mvnCommand = "mvn";
        if (File.separatorChar == '\\') {
            mvnCommand = findExecutableOnPath("mvn.cmd");
            if (mvnCommand == null) {
                mvnCommand = findExecutableOnPath("mvn.bat");
            }
        }
        return mvnCommand;
    }

    private static String findExecutableOnPath(String name) {
        for (String dirname : System.getenv("PATH").split(File.pathSeparator)) {
            File file = new File(dirname, name);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static Set<Path> sourceDirectories(Path pomXml) {
        try {
            Set<Path> all = new HashSet<>();

            // Parse pom.xml
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomXml.toFile());

            // Find source directory
            String sourceDir = XPathFactory.newInstance().newXPath().compile("/project/build/sourceDirectory").evaluate(doc);

            if (sourceDir == null || sourceDir.isEmpty()) {
                LOG.info("Use default source directory src/main/java");

                sourceDir = "src/main/java";
            }
            else LOG.info("Use source directory from pom.xml " + sourceDir);
            
            all.add(pomXml.resolveSibling(sourceDir).toAbsolutePath());

            // Find test directory
            String testDir = XPathFactory.newInstance().newXPath().compile("/project/build/testSourceDirectory").evaluate(doc);

            if (testDir == null || testDir.isEmpty()) {
                LOG.info("Use default test directory src/test/java");

                testDir = "src/test/java";
            }
            else LOG.info("Use test directory from pom.xml " + testDir);
            
            all.add(pomXml.resolveSibling(testDir).toAbsolutePath());

            return all;
        } catch (IOException | ParserConfigurationException | SAXException | XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    private JavaConfigJson readJavaConfigJson(Path configFile) {
        try {
            return JSON.readValue(configFile.toFile(), JavaConfigJson.class);
        } catch (IOException e) {
            MessageParams message = new MessageParams();

            message.setMessage("Error reading " + configFile);
            message.setType(MessageType.Error);

            throw new ShowMessageException(message, e);
        }
    }

    private static Set<Path> readClassPathFile(Path classPathFilePath) {
        try {
            InputStream in = Files.newInputStream(classPathFilePath);
            String text = new BufferedReader(new InputStreamReader(in))
                    .lines()
                    .collect(Collectors.joining());
            Path dir = classPathFilePath.getParent();

            return Arrays.stream(text.split(File.pathSeparator))
                         .map(dir::resolve)
                         .collect(Collectors.toSet());
        } catch (IOException e) {
            MessageParams message = new MessageParams();

            message.setMessage("Error reading " + classPathFilePath);
            message.setType(MessageType.Error);

            throw new ShowMessageException(message, e);
        }
    }

    private static final Logger LOG = Logger.getLogger("main");
}