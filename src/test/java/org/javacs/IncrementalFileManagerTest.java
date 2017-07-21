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
        String sig = test.sourceSignature("com.example.Signatures").orElseThrow(() -> new RuntimeException("com.example.Signatures class not found"));

        assertThat(sig, containsString("public void voidMethod()"));
        assertThat(sig, containsString("public java.lang.String stringMethod()"));
        assertThat(sig, not(containsString("public void privateMethod()")));
        assertThat(sig, containsString("Signatures(int)"));
        assertThat(sig, containsString("StaticInnerClass"));
        assertThat(sig, containsString("void innerMethod()"));
        assertThat(sig, containsString("RegularInnerClass"));
    }

    @Test
    public void classFileSignature() {
        String sig = test.classSignature("com.example.Signatures").orElseThrow(() -> new RuntimeException("com.example.Signatures class not found"));

        assertThat(sig, containsString("public void voidMethod()"));
        assertThat(sig, containsString("public java.lang.String stringMethod()"));
        assertThat(sig, not(containsString("public void privateMethod()")));
        assertThat(sig, containsString("Signatures(int)"));
        assertThat(sig, containsString("StaticInnerClass"));
        assertThat(sig, containsString("void innerMethod()"));
        assertThat(sig, containsString("RegularInnerClass"));
    }

    @Test
    public void simpleSignatureEquals() {
        String classSig = test.classSignature("com.example.Signatures").orElseThrow(() -> new RuntimeException("com.example.Signatures class not found")),
               sourceSig = test.sourceSignature("com.example.Signatures").orElseThrow(() -> new RuntimeException("com.example.Signatures class not found"));

        assertThat(classSig, equalTo(sourceSig));
    }
}