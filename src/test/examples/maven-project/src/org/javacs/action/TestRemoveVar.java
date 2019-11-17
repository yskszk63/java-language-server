package org.javacs.docs.action;

class TestRemoveVar {
    void test() {
        int unusedLocal = makeInt();
    }

    int makeInt() {
        return 1;
    }
}