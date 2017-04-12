package org.javacs;

import java.util.concurrent.CompletableFuture;

class SignatureHelp {
    void test(Runnable r) {
        CompletableFuture.runAsync();
        CompletableFuture.runAsync(r, )
    }
}