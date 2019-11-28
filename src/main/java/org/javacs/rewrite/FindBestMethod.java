package org.javacs.rewrite;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.regex.Pattern;
import javax.lang.model.element.*;
import javax.lang.model.type.*;

class FindBestMethod extends TreeScanner<MethodTree, ExecutableElement> {
    @Override
    public MethodTree visitMethod(MethodTree t, ExecutableElement find) {
        if (isMatch(t, find)) {
            return t;
        }
        return super.visitMethod(t, find);
    }

    @Override
    public MethodTree reduce(MethodTree r1, MethodTree r2) {
        if (r1 != null) return r1;
        return r2;
    }

    private boolean isMatch(MethodTree candidate, ExecutableElement find) {
        if (!candidate.getName().contentEquals(find.getSimpleName())) {
            return false;
        }
        if (candidate.getParameters().size() != find.getParameters().size()) {
            return false;
        }
        if (!typeMatches(candidate.getReturnType(), find.getReturnType())) {
            return false;
        }
        for (var i = 0; i < candidate.getParameters().size(); i++) {
            if (!typeMatches(candidate.getParameters().get(i), find.getParameters().get(i).asType())) {
                return false;
            }
        }
        return true;
    }

    private boolean typeMatches(Tree candidate, TypeMirror find) {
        if (find instanceof PrimitiveType) {
            return candidate.toString().equals(find.toString());
        } else if (find instanceof DeclaredType) {
            var declared = (DeclaredType) find;
            var name = declared.asElement().getSimpleName();
            var pattern = Pattern.compile("^(\\w\\.)*" + name);
            return pattern.matcher(candidate.toString()).find();
        } else if (find instanceof ArrayType) {
            if (!(candidate instanceof ArrayTypeTree)) {
                return false;
            }
            var findArray = (ArrayType) find;
            var candidateArray = (ArrayTypeTree) candidate;
            return typeMatches(candidateArray.getType(), findArray.getComponentType());
        } else {
            return true;
        }
    }
}
