package org.javacs;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;

import javax.lang.model.element.ElementKind;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class Indexer extends BaseScanner {
    private SymbolIndex parent;
    private SourceFileIndex index;

    public Indexer(SymbolIndex parent, Context context) {
        super(context);
        this.parent = parent;
    }

    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        URI uri = tree.getSourceFile().toUri();

        index = new SourceFileIndex();

        super.visitTopLevel(tree);

        parent.put(uri, index);
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        super.visitClassDef(tree);

        addDeclaration(tree, tree.sym);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        super.visitMethodDef(tree);

        addDeclaration(tree, tree.sym);
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        super.visitVarDef(tree);

        addDeclaration(tree, tree.sym);
    }

    @Override
    public void visitSelect(JCTree.JCFieldAccess tree) {
        super.visitSelect(tree);

        addReference(tree, tree.sym);
    }

    @Override
    public void visitReference(JCTree.JCMemberReference tree) {
        super.visitReference(tree);

        addReference(tree, tree.sym);
    }

    @Override
    public void visitIdent(JCTree.JCIdent tree) {
        addReference(tree, tree.sym);
    }

    @Override
    public void visitNewClass(JCTree.JCNewClass tree) {
        super.visitNewClass(tree);

        addReference(tree, tree.constructor);
    }

    private void addDeclaration(JCTree tree, Symbol symbol) {
        if (symbol != null && onSourcePath(symbol) && SymbolIndex.shouldIndex(symbol)) {
            String key = SymbolIndex.uniqueName(symbol);
            SymbolInformation info = symbolInformation(tree, symbol, compilationUnit);
            Map<String, SymbolInformation> withKind = index.declarations.computeIfAbsent(symbol.getKind(), newKind -> new HashMap<>());

            withKind.put(key, info);
        }
    }

    private void addReference(JCTree tree, Symbol symbol) {
        if (symbol != null && onSourcePath(symbol) && SymbolIndex.shouldIndex(symbol)) {
            String key = SymbolIndex.uniqueName(symbol);
            Map<String, Set<Location>> withKind = index.references.computeIfAbsent(symbol.getKind(), newKind -> new HashMap<>());
            Set<Location> locations = withKind.computeIfAbsent(key, newName -> new HashSet<>());
            Location location = SymbolIndex.location(tree, compilationUnit);

            locations.add(location);
        }
    }

    private static SymbolInformation symbolInformation(JCTree tree, Symbol symbol, JCTree.JCCompilationUnit compilationUnit) {
        Location location = SymbolIndex.location(tree, compilationUnit);
        SymbolInformation info = new SymbolInformation();

        info.setContainerName(symbol.getEnclosingElement().getQualifiedName().toString());
        info.setKind(symbolInformationKind(symbol.getKind()));

        // Constructors have name <init>, use class name instead
        if (symbol.getKind() == ElementKind.CONSTRUCTOR)
            info.setName(symbol.getEnclosingElement().getSimpleName().toString());
        else
            info.setName(symbol.getSimpleName().toString());

        info.setLocation(location);

        return info;
    }

    private static SymbolKind symbolInformationKind(ElementKind kind) {
        switch (kind) {
            case PACKAGE:
                return SymbolKind.Package;
            case ENUM:
            case ENUM_CONSTANT:
                return SymbolKind.Enum;
            case CLASS:
                return SymbolKind.Class;
            case ANNOTATION_TYPE:
            case INTERFACE:
                return SymbolKind.Interface;
            case FIELD:
                return SymbolKind.Property;
            case PARAMETER:
            case LOCAL_VARIABLE:
            case EXCEPTION_PARAMETER:
            case TYPE_PARAMETER:
                return SymbolKind.Variable;
            case METHOD:
            case STATIC_INIT:
            case INSTANCE_INIT:
                return SymbolKind.Method;
            case CONSTRUCTOR:
                return SymbolKind.Constructor;
            case OTHER:
            case RESOURCE_VARIABLE:
            default:
                return SymbolKind.String;
        }
    }
    
    private static boolean onSourcePath(Symbol symbol) {
        return true; // TODO
    }
}
