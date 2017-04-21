package org.javacs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
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
    private final Map<Path, List<JavacConfig>> configBySource = new HashMap<>(), configByDir = new HashMap<>();

    FindConfig(Path workspaceRoot, Optional<JavacConfig> testConfig) {
        this.workspaceRoot = workspaceRoot;
        this.testConfig = testConfig;
    }

    Optional<JavacConfig> forFile(Path file) {
        if (!file.toFile().isDirectory())
            file = file.getParent();

        if (file == null)
            return Optional.empty();

        List<JavacConfig> found = configBySource.computeIfAbsent(file, this::doFindConfig);

        return chooseConfig(found, file);
    }

    private List<JavacConfig> doFindConfig(final Path sourceDir) {
        if (testConfig.isPresent())
            return ImmutableList.of(testConfig.get());

        Path dir = sourceDir;

        while (true) {
            List<JavacConfig> found = readIfConfig(dir);

            if (!found.isEmpty()) {
                LOG.info("Found " + dir + "/javaconfig.json for " + sourceDir);

                return found;
            }
            else if (workspaceRoot.startsWith(dir))
                return Collections.emptyList();
            else
                dir = dir.getParent();
        }
    }

    private Optional<JavacConfig> chooseConfig(List<JavacConfig> found, Path dir) {
        return found.stream()
            .filter(config -> matchesDir(config, dir))
            .findFirst();
    }

    private boolean matchesDir(JavacConfig config, Path sourceDir) {
        for (Path each : config.sourcePath) {
            if (sourceDir.startsWith(each)) 
                return true;
        }

        return false;
    }

    /**
     * If directory contains a config file, for example javaconfig.json or an eclipse project file, read it.
     */
    private List<JavacConfig> readIfConfig(Path dir) {
        return configByDir.computeIfAbsent(dir, this::doReadIfConfig);
    }

    private List<JavacConfig> doReadIfConfig(Path dir) {
        Function<JavaConfigJson, JavacConfig> parseJavaConfigJson = json -> {
            Set<Path> classPath = readClassPath(dir, json.classPath, json.classPathFile),
                      docPath = readClassPath(dir, json.docPath, json.docPathFile);
            Set<Path> sourcePath = json.sourcePath.stream().map(dir::resolve).collect(Collectors.toSet());
            Path outputDirectory = dir.resolve(json.outputDirectory);

            return new JavacConfig(sourcePath, classPath, outputDirectory, CompletableFuture.completedFuture(docPath));
        };
        if (Files.exists(dir.resolve("javaconfig.json"))) {
            return readJavaConfigJson(dir.resolve("javaconfig.json")).stream()
                .map(parseJavaConfigJson)
                .collect(Collectors.toList());
        }
        else if (Files.exists(dir.resolve("pom.xml"))) {
            return ImmutableList.of(
                readPomXml(dir, false), 
                readPomXml(dir, true)
            );
        }
        // TODO add more file types
        else {
            return Collections.emptyList();
        }
    }

    private Set<Path> readClassPath(Path dir, Set<Path> jsonClassPath, Optional<Path> jsonClassPathFile) {
        Set<Path> classPath = new HashSet<>();

        jsonClassPathFile.ifPresent(classPathFile -> {
            Path classPathFilePath = dir.resolve(classPathFile);
            Set<Path> paths = readClassPathFile(classPathFilePath);

            classPath.addAll(paths);
        });

        jsonClassPath.forEach(entry -> classPath.add(dir.resolve(entry)));

        return classPath;
    }

    private JavacConfig readPomXml(Path dir, boolean testScope) {
        Path originalPom = dir.resolve("pom.xml");
        Path effectivePom = generateEffectivePom(originalPom);

        // Invoke maven to get classpath
        Set<Path> classPath = buildClassPath(originalPom, testScope, false);

        // Get source directory from pom.xml
        Set<Path> sourcePath = sourceDirectories(effectivePom, testScope);

        // Use maven output directory so incremental compilation uses maven-generated .class files
        Path outputDirectory = testScope ? 
            Paths.get("target/test-classes").toAbsolutePath() : 
            Paths.get("target/classes").toAbsolutePath();

        JavacConfig config = new JavacConfig(
            sourcePath, 
            classPath, 
            outputDirectory,
            CompletableFuture.supplyAsync(() -> buildClassPath(originalPom, testScope, true))
        );

        LOG.info("Inferred from " + originalPom + ":");
        LOG.info("\tsourcePath: " + Joiner.on(" ").join(sourcePath));
        LOG.info("\tclassPath: " + Joiner.on(" ").join(classPath));
        LOG.info("\tdocPath: (pending)");
        LOG.info("\toutputDirectory: " + outputDirectory);

        return config;
    }

    private static Path generateEffectivePom(Path pomXml) {
        try {
            Objects.requireNonNull(pomXml, "pom.xml path is null");

            Path effectivePom = Files.createTempFile("effective-pom", ".xml");

            LOG.info(String.format("Emit effective pom for %s to %s", pomXml, effectivePom));

            String cmd = String.format(
                "%s help:effective-pom -Doutput=%s",
                getMvnCommand(),
                effectivePom
            );
            File workingDirectory = pomXml.toAbsolutePath().getParent().toFile();
            int result = Runtime.getRuntime().exec(cmd, null, workingDirectory).waitFor();

            if (result != 0)
                throw new RuntimeException("`" + cmd + "` returned " + result);

            return effectivePom;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO For sourceJars = true:
    // use mvn dependency:list to list the dependencies quickly, 
    // then fetch each one individually using mvn dependency:build-classpath -DincludeGroupIds=? -DincludeArtifactIds=? ...
    private static Set<Path> buildClassPath(Path pomXml, boolean testScope, boolean sourceJars) {
        try {
            Objects.requireNonNull(pomXml, "pom.xml path is null");

            // Tell maven to output classpath to a temporary file
            // TODO if pom.xml already specifies outputFile, use that location
            Path classPathTxt = Files.createTempFile(sourceJars ? "sourcepath" : "classpath", ".txt");

            LOG.info(String.format(
                "Emit %s to %s",
                sourceJars ? "docPath" : "classpath",
                classPathTxt
            ));

            String cmd = String.format(
                "%s dependency:build-classpath -DincludeScope=%s -Dmdep.outputFile=%s %s",
                getMvnCommand(),
                testScope ? "test" : "compile",
                classPathTxt,
                sourceJars ? "-Dclassifier=sources" : ""
            );
            File workingDirectory = pomXml.toAbsolutePath().getParent().toFile();
            int result = Runtime.getRuntime().exec(cmd, null, workingDirectory).waitFor();

            if (result != 0)
                throw new RuntimeException("`" + cmd + "` returned " + result);

            Set<Path> found = readClassPathFile(classPathTxt);

            LOG.info("Read " + Joiner.on(" ").join(found) + " from " + classPathTxt);

            return found;
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

    private static Set<Path> sourceDirectories(Path pomXml, boolean testScope) {
        return testScope ? 
            ImmutableSet.of(onlySourceDirectories(pomXml, true), onlySourceDirectories(pomXml, false)) :
            ImmutableSet.of(onlySourceDirectories(pomXml, false));
    }

    private static Path onlySourceDirectories(Path pomXml, boolean testScope) {
        String defaultSourceDir = testScope ? "src/test/java" : "src/main/java";
        String xPath = testScope ? "/project/build/testSourceDirectory" : "/project/build/sourceDirectory";
        Document doc = parsePomXml(pomXml);

        try {
            String sourceDir = XPathFactory.newInstance().newXPath().compile(xPath).evaluate(doc);

            if (sourceDir == null || sourceDir.isEmpty()) {
                LOG.info("Use default source directory " + defaultSourceDir);

                sourceDir = defaultSourceDir;
            }
            else LOG.info("Use source directory from pom.xml " + sourceDir);
            
            return pomXml.resolveSibling(sourceDir).toAbsolutePath();
        } catch (XPathExpressionException e) {
                throw new RuntimeException(e);
        }
    }

    private static Document parsePomXml(Path pomXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            return builder.parse(pomXml.toFile());
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private List<JavaConfigJson> readJavaConfigJson(Path configFile) {
        try {
            JsonNode json = JSON.readValue(configFile.toFile(), JsonNode.class);

            if (json.isArray())
                return JSON.convertValue(json, new TypeReference<List<JavaConfigJson>>() { });
            else {
                JavaConfigJson one = JSON.convertValue(json, JavaConfigJson.class);

                return ImmutableList.of(one);
            }
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