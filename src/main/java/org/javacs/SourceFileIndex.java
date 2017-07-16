package org.javacs;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;

class SourceFileIndex {
    String packageName = "";

    /**
     * Simple names of classes declared in this file
     */
    final Set<String> publicTopLevelClasses = new HashSet<>(), 
                      packagePrivateTopLevelClasses = new HashSet<>();

    /**
     * Simple name of declarations in this file, including classes, methods, and fields
     */
    final Set<String> declarations = new HashSet<>();

    /**
     * Simple names of reference appearing in this file.
     * 
     * This is fast to compute and provides a useful starting point for find-references operations.
     */
    final Set<String> references = new HashSet<>();

    final Instant updated = Instant.now();
}
