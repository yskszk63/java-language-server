package org.javacs.docs.action;

import java.util.Optional;

class TestSuppressWarning {
    void test(Optional o) {
        o.orElse(1);
    }
}