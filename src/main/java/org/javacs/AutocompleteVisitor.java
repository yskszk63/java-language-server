package org.javacs;

import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.*;
import com.google.common.base.Joiner;
import com.sun.source.tree.*;
import com.sun.tools.javac.api.JavacScope;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.Name;
import io.typefox.lsapi.CompletionItem;
import io.typefox.lsapi.CompletionItemImpl;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.JavaFileObject;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AutocompleteVisitor extends CursorScanner {
    private static final Logger LOG = Logger.getLogger("main");
    public final List<CompletionItemImpl> suggestions = new ArrayList<>();

    public AutocompleteVisitor(JavaFileObject file, long cursor, Context context) {
        super(file, cursor, context);
    }

    /**
     * [expression].[identifier]
     */
    @Override
    public void visitSelect(JCTree.JCFieldAccess node) {
        // If expression contains cursor, no autocomplete
        JCTree.JCExpression expression = node.getExpression();

        if (containsCursor(expression))
            super.visitSelect(node);
        else {
            TypeMirror type = expression.type;

            if (type == null)
                LOG.warning("No type for " + expression);
            else if (isClassReference(expression)) {
                CompletionItemImpl item = new CompletionItemImpl();

                item.setKind(CompletionItem.KIND_PROPERTY);
                item.setLabel("class");
                item.setInsertText("class");

                suggestions.add(item);

                type.accept(new CollectStatics(), null);
            }
            else if (type.getKind() == TypeKind.PACKAGE) {
                // Tell ClassReader to scan the given package name
                Names names = Names.instance(context);
                ClassReader reader = ClassReader.instance(context);
                Name prefix = names.fromString(type.toString());

                reader.enterPackage(prefix);

                // Symtab.packages should now be filled in with all sub-packages
                Symtab symtab = Symtab.instance(context);

                for (Symbol.PackageSymbol p : symtab.packages.values()) {
                    if (p.owner != null && p.owner.getQualifiedName().equals(prefix)) {
                        Name end = p.getSimpleName();

                        CompletionItemImpl item = new CompletionItemImpl();

                        item.setKind(CompletionItem.KIND_MODULE);
                        item.setLabel(end.toString());
                        item.setInsertText(end.toString());
                        item.setSortText("0/" + end.toString());

                        suggestions.add(item);
                    }
                }

                for (Symbol.ClassSymbol c : symtab.classes.values()) {
                    if (c.owner != null && c.owner.getQualifiedName().equals(prefix)) {
                        Name end = c.getSimpleName();

                        CompletionItemImpl item = new CompletionItemImpl();

                        item.setKind(CompletionItem.KIND_CLASS);
                        item.setLabel(end.toString());
                        item.setInsertText(end.toString());
                        item.setSortText("1/" + end.toString());

                        suggestions.add(item);
                    }
                }
            }
            else
                type.accept(new CollectVirtuals(), null);
        }
    }

    private boolean isClassReference(ExpressionTree expression) {
        return expression instanceof JCTree.JCIdent &&
               ((JCTree.JCIdent) expression).sym instanceof Symbol.ClassSymbol;
    }

    @Override
    public void visitReference(JCTree.JCMemberReference node) {
        // If expression contains cursor, no autocomplete
        JCTree.JCExpression expression = node.getQualifierExpression();

        if (containsCursor(expression))
            super.visitReference(node);
        else {
            TypeMirror type = expression.type;

            if (type == null)
                LOG.warning("No type for " + expression);
            else if (isClassReference(expression)) {
                CompletionItemImpl item = new CompletionItemImpl();

                item.setKind(CompletionItem.KIND_METHOD);
                item.setLabel("new");
                item.setInsertText("new");

                suggestions.add(item);

                type.accept(new CollectStatics(), null);
            }
            else
                type.accept(new CollectVirtuals(), null);
        }
    }

    @Override
    public void visitNewClass(JCTree.JCNewClass tree) {
        scan(tree.encl);
        scan(tree.typeargs);
        scan(tree.args);
        scan(tree.def);

        if (containsCursor(tree.clazz)) {
            tree.clazz.accept(new CursorScanner(file, cursor, context) {
                {
                    compilationUnit = AutocompleteVisitor.this.compilationUnit;
                }

                @Override
                public void visitIdent(JCTree.JCIdent tree) {
                    super.visitIdent(tree);

                    TreePath path = getPath(new TreePath(compilationUnit), tree);

                    if (path != null) {
                        JavacTrees trees = JavacTrees.instance(context);
                        JavacScope scope = trees.getScope(path);

                        // Add local elements from each surrounding scope
                        JavacScope upScope = scope;

                        while (upScope != null) {
                            for (Element e : upScope.getLocalElements()) {
                                addConstructorIfClass(e);
                            }

                            upScope = upScope.getEnclosingScope();
                        }

                        // Get inner classes
                        List<Element> locals = localElements(scope);

                        for (Element e : locals) {
                            addConstructorIfClass(e);
                        }

                        // Get package classes
                        List<Symbol.ClassSymbol> classes = packageClasses(scope);

                        for (Symbol.ClassSymbol c : classes) {
                            addConstructor(c);
                        }
                    }
                }

                private void addConstructorIfClass(Element e) {
                    if (e.getKind() == ElementKind.CLASS ||
                        e.getKind() == ElementKind.INTERFACE) {
                        if (e instanceof Symbol.ClassSymbol) {
                            addConstructor((Symbol.ClassSymbol) e);
                        }
                        else LOG.warning("Expected ClassSymbol but found " + e.getClass());
                    }
                }

                private void addConstructor(Symbol.ClassSymbol symbol) {
                    Name name = symbol.getSimpleName();
                    String insertText = name.toString();

                    CompletionItemImpl item = new CompletionItemImpl();

                    item.setKind(CompletionItem.KIND_CONSTRUCTOR);
                    item.setLabel(name.toString());
                    item.setInsertText(insertText);
                    item.setSortText("0/" + name.toString());
                    item.setFilterText(name.toString());

                    suggestions.add(item);
                }
            });
        }

    }

    @Override
    public void visitIdent(JCTree.JCIdent node) {
        super.visitIdent(node);

        TreePath path = getPath(new TreePath(compilationUnit), node);

        if (path != null) {
            JavacTrees trees = JavacTrees.instance(context);
            JavacScope scope = trees.getScope(path);
            AttrContext info = scope.getEnv().info;
            boolean isStatic = AttrUtils.isStatic(info);

            // Add local elements from each surrounding scope
            JavacScope upScope = scope;

            while (upScope != null) {
                for (Element e : upScope.getLocalElements()) {
                    addElement(e);
                }

                upScope = upScope.getEnclosingScope();
            }

            // Add class symbols
            final TypeElement enclosingClass = scope.getEnclosingClass();

            if (enclosingClass != null) {
                // Add inner classes
                // TODO is this not handled by scope?
                List<Element> elements = localElements(scope);

                for (Element e : elements) {
                    boolean include = !isStatic || e.getModifiers().contains(Modifier.STATIC);

                    if (include)
                        addElement(e);
                }

                // Add package members
                List<Symbol.ClassSymbol> packageClasses = packageClasses(scope);

                for (Symbol.ClassSymbol c : packageClasses) {
                    Name end = c.getSimpleName();

                    CompletionItemImpl item = new CompletionItemImpl();

                    item.setKind(CompletionItem.KIND_CLASS);
                    item.setLabel(end.toString());
                    item.setInsertText(end.toString());
                    item.setSortText(end.toString());

                    suggestions.add(item);
                }
            }
        }
        else {
            LOG.info("Node " + node + " not found in compilation unit " + compilationUnit.getSourceFile());
        }
    }

    private List<Element> localElements(JavacScope scope) {
        Element enclosingClass = scope.getEnclosingClass();
        List<Element> result = new ArrayList<>();

        while (enclosingClass != null && enclosingClass.getKind() == ElementKind.CLASS) {
            result.addAll(enclosingClass.getEnclosedElements());

            enclosingClass = enclosingClass.getEnclosingElement();
        }

        return result;
    }

    private List<Symbol.ClassSymbol> packageClasses(JavacScope scope) {
        Element enclosingPackage = scope.getEnclosingClass();

        while (enclosingPackage != null && enclosingPackage.getKind() != ElementKind.PACKAGE)
            enclosingPackage = enclosingPackage.getEnclosingElement();

        List<Symbol.ClassSymbol> result = new ArrayList<>();

        if (enclosingPackage != null) {
            // Tell ClassReader to scan the given package name
            Names names = Names.instance(context);
            ClassReader reader = ClassReader.instance(context);
            Name prefix = names.fromString(enclosingPackage.toString());

            reader.enterPackage(prefix);

            // Symtab.packages should now be filled in with all sub-packages
            Symtab symtab = Symtab.instance(context);

            for (Symbol.ClassSymbol c : symtab.classes.values()) {
                if (c.owner != null && c.owner.getQualifiedName().equals(prefix)) {
                    result.add(c);
                }
            }
        }

        return result;
    }

    /**
     * Gets a tree path for a tree node within a subtree identified by a TreePath object.
     * @return null if the node is not found
     */
    private static TreePath getPath(TreePath path, Tree target) {
        path.getClass();
        target.getClass();

        class Result extends Error {
            static final long serialVersionUID = -5942088234594905625L;
            TreePath path;
            Result(TreePath path) {
                this.path = path;
            }
        }

        class PathFinder extends TreePathScanner<TreePath,Tree> {
            public TreePath scan(Tree tree, Tree target) {
                if (tree == target) {
                    throw new Result(new TreePath(getCurrentPath(), target));
                }
                return super.scan(tree, target);
            }

            @Override
            public TreePath visitErroneous(ErroneousTree node, Tree tree) {
                return super.scan(node.getErrorTrees(), tree);
            }
        }

        if (path.getLeaf() == target) {
            return path;
        }

        try {
            new PathFinder().scan(path, target);
        } catch (Result result) {
            return result.path;
        }
        return null;
    }

    private void addElement(Element e) {
        try {
            String name = e.getSimpleName().toString();

            switch (e.getKind()) {
                case PACKAGE:
                    break;
                case ENUM:
                case CLASS:
                case ANNOTATION_TYPE:
                case INTERFACE:
                case TYPE_PARAMETER: {
                    CompletionItemImpl item = new CompletionItemImpl();

                    item.setKind(CompletionItem.KIND_INTERFACE);
                    item.setLabel(name);
                    item.setInsertText(name);
                    item.setSortText("2/" + name);

                    suggestions.add(item);

                    break;
                }
                case ENUM_CONSTANT: {
                    CompletionItemImpl item = new CompletionItemImpl();

                    item.setKind(CompletionItem.KIND_ENUM);
                    item.setLabel(name);
                    item.setInsertText(name);
                    item.setDetail(e.getEnclosingElement().getSimpleName().toString());
                    item.setSortText("2/" + name);

                    suggestions.add(item);

                    break;
                }
                case FIELD:
                    addField((Symbol.VarSymbol) e, 1);

                    break;
                case PARAMETER:
                case LOCAL_VARIABLE:
                case EXCEPTION_PARAMETER: {
                    CompletionItemImpl item = new CompletionItemImpl();

                    item.setKind(CompletionItem.KIND_VARIABLE);
                    item.setLabel(name);
                    item.setInsertText(name);
                    item.setSortText("0/" + name);

                    suggestions.add(item);

                    break;
                }
                case METHOD:
                    addMethod((Symbol.MethodSymbol) e, 1);

                    break;
                case CONSTRUCTOR:
                    // TODO
                    break;
                case STATIC_INIT:
                    // Nothing user-enterable
                    break;
                case INSTANCE_INIT:
                    // Nothing user-enterable
                    break;
                case OTHER:
                    break;
                case RESOURCE_VARIABLE:
                    break;
            }
        } catch (ClassReader.BadClassFile bad) {
            // Element#getKind() sometimes throws this when it finds something on the class path it doesn't like
            // We just skip that element and log a warning
            LOG.log(Level.WARNING, bad.getMessage(), bad);
        }
    }

    private void addMethod(Symbol.MethodSymbol e, int superRemoved) {
        String label = methodSignature(e);
        CompletionItemImpl item = new CompletionItemImpl();

        item.setKind(CompletionItem.KIND_METHOD);
        item.setLabel(label);
        item.setDetail(ShortTypePrinter.print(e.getReturnType()));
        item.setDocumentation(docstring(e));
        item.setInsertText(e.getSimpleName().toString());
        item.setSortText(superRemoved + "/" + label);
        item.setFilterText(e.getSimpleName().toString());

        suggestions.add(item);
    }

    public static String methodSignature(Symbol.MethodSymbol e) {
        String name = e.getSimpleName().toString();
        boolean varargs = (e.flags() & Flags.VARARGS) != 0;
        StringJoiner params = new StringJoiner(", ");

        com.sun.tools.javac.util.List<Symbol.VarSymbol> parameters = e.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            Symbol.VarSymbol p = parameters.get(i);
            String pName = shortName(p, varargs && i == parameters.size() - 1);

            params.add(pName);
        }

        String signature = name + "(" + params + ")";
        
        if (!e.getThrownTypes().isEmpty()) {
            StringJoiner thrown = new StringJoiner(", ");
            
            for (Type t : e.getThrownTypes()) 
                thrown.add(ShortTypePrinter.print(t));
                
            signature += " throws " + thrown;
        }
        
        return signature;
    }

    private static String shortName(Symbol.VarSymbol p, boolean varargs) {
        Type type = p.type;

        if (varargs) {
            Type.ArrayType array = (Type.ArrayType) type;

            type = array.getComponentType();
        }

        String acc = shortTypeName(type);
        String name = p.name.toString();

        if (varargs)
            acc += "...";

        if (!name.matches("arg\\d+"))
            acc += " " + name;

        return acc;
    }

    private static String shortTypeName(Type type) {
        return ShortTypePrinter.print(type);
    }

    private String docstring(Symbol symbol) {
        JavacTrees trees = JavacTrees.instance(context);
        TreePath path = trees.getPath(symbol);

        if (path != null)
            return trees.getDocComment(path);
        else
            return null;
    }

    private void addField(Symbol.VarSymbol e, int sortOrder) {
        String name = e.getSimpleName().toString();

        CompletionItemImpl item = new CompletionItemImpl();

        item.setKind(CompletionItem.KIND_PROPERTY);
        item.setLabel(name);
        item.setInsertText(name);
        item.setDetail(ShortTypePrinter.print(e.type));
        item.setDocumentation(docstring(e));
        item.setSortText(sortOrder + "/" + name);

        suggestions.add(item);
    }

    private class CollectStatics extends BridgeTypeVisitor {

        @Override
        public void visitDeclared(DeclaredType t) {
            TypeElement typeElement = (TypeElement) t.asElement();
            List<? extends Element> members = JavacElements.instance(context).getAllMembers(typeElement);

            for (Element e : members) {
                switch (e.getKind()) {
                    case FIELD:
                        Symbol.VarSymbol field = (Symbol.VarSymbol) e;

                        if (field.isStatic())
                            addField(field, 0);

                        break;
                    case METHOD:
                        Symbol.MethodSymbol method = (Symbol.MethodSymbol) e;

                        if (method.isStatic())
                            addMethod(method, 0);

                        break;
                }
            }
        }
    }

    private class CollectVirtuals extends BridgeTypeVisitor {
        @Override
        public void visitArray(ArrayType t) {
            // Array types just have 'length'
            CompletionItemImpl item = new CompletionItemImpl();

            item.setLabel("length");
            item.setInsertText("length");
            item.setKind(CompletionItem.KIND_PROPERTY);

            suggestions.add(item);
        }

        @Override
        public void visitTypeVariable(TypeVariable t) {
            visit(t.getUpperBound());
        }

        @Override
        public void visitDeclared(DeclaredType t) {
            TypeElement typeElement = (TypeElement) t.asElement();
            List<? extends Element> members = JavacElements.instance(context).getAllMembers(typeElement);

            for (Element e : members) {
                switch (e.getKind()) {
                    case FIELD:
                        Symbol.VarSymbol field = (Symbol.VarSymbol) e;

                        if (!field.isStatic()) {
                            int removed = supersRemoved(field, t);
                            
                            addField(field, 0);
                        }

                        break;
                    case METHOD:
                        Symbol.MethodSymbol method = (Symbol.MethodSymbol) e;

                        if (!method.isStatic()) {
                            int removed = supersRemoved(method, t);
                            
                            addMethod(method, removed);
                        }

                        break;
                }
            }
        }

        @Override
        public void visitWildcard(WildcardType t) {
            visit(t.getExtendsBound());
        }
    }

    /**
     * When autocompleting [inType].[member], is [member] part of [inType], or a superclass?
     */
    private int supersRemoved(Symbol member, DeclaredType inType) {
        Element inElement = inType.asElement();
        Symbol memberType = member.getEnclosingElement();

        // If member is a member of java.lang.Object, sort order 2
        if (memberType.getQualifiedName().contentEquals("java.lang.Object"))
            return 2;
        // If member is inherited, sort order 1
        else if (!memberType.equals(inElement))
            return 1;
        // If member is not inherited, sort order 0
        else
            return 0;
    }
}
