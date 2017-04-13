package org.javacs;

import java.util.concurrent.CompletableFuture;

class SignatureHelp {
    void test(Runnable r) {
        CompletableFuture.runAsync();
        CompletableFuture.runAsync(r, );
        new SignatureHelp()
    }

    /**
     * A constructor
     */
    SignatureHelp(String name) {
        // Nothing to do
    }
}