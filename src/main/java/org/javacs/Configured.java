package org.javacs;

class Configured {
    final JavacHolder compiler;
    final Javadocs docs;
    final SymbolIndex index;
    final FindSymbols find;

    Configured(JavacHolder compiler, Javadocs docs, SymbolIndex index, FindSymbols find) {
        this.compiler = compiler;
        this.docs = docs;
        this.index = index;
        this.find = find;
    }
}
