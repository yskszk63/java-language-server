package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.tools.*;

class Index {
    /** map[ptr] is the number of references to ptr */
    private final Map<Ptr, Integer> referenceCounts = new HashMap<>();
    /** hasErrors is true if there were compilation errors when we created this index */
    private boolean hasErrors;

    Index(
            JavacTask task,
            CompilationUnitTree from,
            Collection<Diagnostic<? extends JavaFileObject>> errors,
            Collection<Element> to) {
        // Scan from for references
        var finder = new FindReferences(task);
        var refs = new HashMap<Element, List<TreePath>>();
        for (var el : to) {
            refs.put(el, new ArrayList<>());
        }
        finder.scan(from, refs);

        // Convert Map<Element, List<_>> to Map<Ptr, Integer>
        for (var el : to) {
            var ptr = new Ptr(el);
            var count = refs.get(el).size();
            referenceCounts.put(ptr, count);
        }

        // Check if there are any errors in from
        var fromUri = from.getSourceFile().toUri();
        for (var err : errors) {
            if (err.getSource().toUri().equals(fromUri)) hasErrors = true;
        }
    }

    boolean needsUpdate(Set<Ptr> signature) {
        if (hasErrors) return true;
        for (var expected : referenceCounts.keySet()) {
            if (!signature.contains(expected)) return true;
        }
        return false;
    }

    int count(Ptr to) {
        return referenceCounts.getOrDefault(to, 0);
    }
}
