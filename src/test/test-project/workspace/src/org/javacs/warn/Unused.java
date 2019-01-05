package org.javacs.warn;

import java.util.function.Consumer;

class Unused {
    void test(int unusedParam) {
        int unusedLocal = 1;
    }

    private int unusedPrivate;

    Consumer<Integer> lambda = unusedLambdaParam -> {
        int unusedLocalInLambda;
    };
}