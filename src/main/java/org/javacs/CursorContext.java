package org.javacs;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;

enum CursorContext {
    NewClass(Tree.Kind.NEW_CLASS),
    Import(Tree.Kind.IMPORT),
    Other(null);

    final Tree.Kind kind;

    TreePath find(final TreePath path) {
        if (this.kind == null)
            return path;
        else {
            TreePath search = path;

            while (search != null) {
                if (this.kind == search.getLeaf().getKind())
                    return search;
                else
                    search = search.getParentPath();
            }

            return path;
        }
    }

    CursorContext(Tree.Kind kind) {
        this.kind = kind;
    }

    /**
     * Is this identifier or member embedded in an important context, for example:
     *
     *   new OuterClass.InnerClass|
     *   import package.Class|
     */
    static CursorContext from(TreePath path) {
        if (path == null)
            return Other;
        else switch (path.getLeaf().getKind()) {
            case MEMBER_SELECT:
            case MEMBER_REFERENCE:
            case IDENTIFIER:
                return from(path.getParentPath());
            case NEW_CLASS:
                return NewClass;
            case IMPORT:
                return Import;
            default:
                return Other;
        }
    }
}
