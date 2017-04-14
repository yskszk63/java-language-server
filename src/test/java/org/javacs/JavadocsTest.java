package org.javacs;

import com.google.common.collect.ImmutableList;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.RootDoc;
import com.sun.tools.javadoc.api.JavadocTool;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import javax.tools.*;
import javax.tools.DocumentationTool.DocumentationTask;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class JavadocsTest {

    static {
        Main.setRootFormat();
    }

    @Test
    public void findSystemDoc() throws IOException {
        RootDoc root = new Javadocs(Collections.emptySet()).index("java.util");

        assertThat(root.classes(), not(emptyArray()));
    }
}