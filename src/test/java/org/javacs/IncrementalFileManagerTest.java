package org.javacs;

import com.google.common.collect.ImmutableList;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.tools.StandardLocation;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;

public class IncrementalFileManagerTest {
    private JavacFileManager delegate = JavacTool.create().getStandardFileManager(__ -> { }, null, Charset.defaultCharset());
    private IncrementalFileManager test = new IncrementalFileManager(delegate);
    private File sourcePath = Paths.get("./src/test/test-project/workspace/src").toFile();
    private File classPath = Paths.get("./src/test/test-project/workspace/out").toFile();

    @Before
    public void setPaths() throws IOException {
        delegate.setLocation(StandardLocation.SOURCE_PATH, ImmutableList.of(sourcePath));
        delegate.setLocation(StandardLocation.CLASS_PATH, ImmutableList.of(classPath));
    }

    @Test
    public void sourceFileSignature() {
        IncrementalFileManager.ClassSig sig = test.sourceSignature("com.example.Test");

        assertThat(sig.methods, hasKey("test"));
    }

    @Test
    public void classFileSignature() {
        IncrementalFileManager.ClassSig sig = test.classSignature("com.example.Test");

        assertThat(sig.methods, hasKey("test"));
    }

    @Test
    public void simpleSignatureEquals() {
        IncrementalFileManager.ClassSig classSig = test.classSignature("com.example.Test"),
                                        sourceSig = test.sourceSignature("com.example.Test");

        assertThat(classSig, equalTo(sourceSig));
    }
}