package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class ChildFirstClassLoaderTest {
    class ExceptionClassLoader extends ClassLoader {
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            throw new RuntimeException("This should never be called");
        }
    }

    @Test
    public void dontCallParent() throws ClassNotFoundException {
        String[] packages = {"org.javacs"};
        ClassLoader childFirst =
                ChildFirstClassLoader.fromClassPath(
                        System.getProperty("java.class.path"),
                        packages,
                        new ExceptionClassLoader());
        Class<?> found = childFirst.loadClass("org.javacs.ChildFirstClassLoaderTest");
        assertThat("found ChildFirstClassLoaderTest", found, not(nullValue()));
        assertThat(
                "reloaded ChildFirstClassLoaderTest",
                found,
                not(equalTo(ChildFirstClassLoaderTest.class)));
    }
}
