package org.javacs;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;

import javax.lang.model.element.ElementKind;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Contains all symbol declarations and references in a single source file
 */
class SourceFileIndex {
    final EnumMap<ElementKind, Map<String, SymbolInformation>> declarations = new EnumMap<>(ElementKind.class);
    final EnumMap<ElementKind, Map<String, Set<Location>>> references = new EnumMap<>(ElementKind.class);
}
