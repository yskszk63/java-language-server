package com.fivetran.javac;

import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

public class BaseScanner extends TreeScanner {
    protected final Context context;
    protected JCTree.JCCompilationUnit compilationUnit;
    protected TreePath path;

    public BaseScanner(Context context) {
        this.context = context;
    }

    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        this.path = new TreePath(tree);
        this.compilationUnit = tree;

        super.visitTopLevel(tree);
    }

    @Override
    public void scan(JCTree node) {
        if (node != null) {
            TreePath prev = path;

            path = new TreePath(path, node);

            try {
                node.accept(this);
            } finally {
                path = prev;
            }
        }
    }

    @Override
    public void scan(List<? extends JCTree> nodes) {
        if (nodes != null) {
            boolean first = true;

            for (JCTree node : nodes) {
                if (first)
                    scan(node);
                else
                    scan(node);

                first = false;
            }
        }
    }

    @Override
    public void visitErroneous(JCTree.JCErroneous tree) {
        scan(tree.errs);
    }
}
