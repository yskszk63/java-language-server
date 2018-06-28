module org.javacs {
    requires java.base;
    requires jdk.compiler;
    requires jdk.javadoc;
    requires java.logging;
    requires com.google.common;
    requires org.eclipse.lsp4j;
    requires com.fasterxml.jackson.core;
    requires remark;
    requires junit;
    requires hamcrest.all;

    exports org.javacs;
}
