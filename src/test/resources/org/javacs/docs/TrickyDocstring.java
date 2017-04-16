package org.javacs.docs;

import java.util.*;

class TrickyDocstring {

    void test() {
        example("foo", new String[] { "foo" }, null);
        parameterized("foo");
    }

    /**
     * Docstring!
     */
    void example(String foo, String[] array, List<String> list) {

    }

    /**
     * Another docstring!
     */
    <T> void parameterized(T foo) {

    }
}