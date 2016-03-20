package com.fivetran.javac;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.util.Context;

import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Override TreeVisitor with an imperative API that avoids map-reduce style
 */
public class BridgeExpressionScanner implements TreeVisitor<Void, Void> {

    public Context context;

    private TreePath path;

    private JavacTrees trees;

    private CompilationUnitTree compilationUnit;
    private Types types;
    private Elements elements;

    /**
     * Path to the current node
     */
    public TreePath path() {
        return path;
    }

    public JavacTrees trees() {
        return trees;
    }

    public Types types() {
        return types;
    }

    public Elements elements() {
        return elements;
    }

    public CompilationUnitTree compilationUnit() {
        return compilationUnit;
    }

    public void scan(Tree node) {
        if (node != null) {
            TreePath prev = path;

            path = new TreePath(path, node);

            try {
                node.accept(this, null);
            } finally {
                path = prev;
            }
        }
    }

    public void scan(Iterable<? extends Tree> nodes) {
        if (nodes != null) {
            boolean first = true;

            for (Tree node : nodes) {
                if (first)
                    scan(node);
                else
                    scan(node);

                first = false;
            }
        }
    }

    public final Void visitCompilationUnit(CompilationUnitTree node, Void p) {
        visitCompilationUnit(node);

        return null;
    }

    protected void visitCompilationUnit(CompilationUnitTree node) {
        path = new TreePath(node);
        compilationUnit = node;
        trees = JavacTrees.instance(context);
        types = JavacTypes.instance(context);
        elements = JavacElements.instance(context);

        scan(node.getPackageAnnotations());

        scan(node.getPackageName());

        scan(node.getImports());

        scan(node.getTypeDecls());
    }

    public final Void visitImport(ImportTree node, Void p) {
        visitImport(node);

        return null;
    }

    protected void visitImport(ImportTree node) {
        scan(node.getQualifiedIdentifier());
    }

    public final Void visitClass(ClassTree node, Void p) {
        visitClass(node);

        return null;
    }

    protected void visitClass(ClassTree node) {
        scan(node.getModifiers());

        scan(node.getTypeParameters());

        scan(node.getExtendsClause());

        scan(node.getImplementsClause());

        scan(node.getMembers());
    }

    public final Void visitMethod(MethodTree node, Void p) {
        visitMethod(node);

        return null;
    }

    protected void visitMethod(MethodTree node) {
        scan(node.getModifiers());

        scan(node.getReturnType());

        scan(node.getTypeParameters());

        scan(node.getParameters());

        scan(node.getReceiverParameter());

        scan(node.getThrows());

        scan(node.getBody());

        scan(node.getDefaultValue());
    }

    public final Void visitVariable(VariableTree node, Void p) {
        visitVariable(node);

        return null;
    }

    protected void visitVariable(VariableTree node) {
        scan(node.getModifiers());

        scan(node.getType());

        scan(node.getNameExpression());

        scan(node.getInitializer());
    }

    public final Void visitEmptyStatement(EmptyStatementTree node, Void p) {
        visitEmptyStatement(node);

        return null;
    }

    protected void visitEmptyStatement(EmptyStatementTree node) {

    }

    public final Void visitBlock(BlockTree node, Void p) {
        visitBlock(node);

        return null;
    }

    protected void visitBlock(BlockTree node) {
        scan(node.getStatements());
    }

    public final Void visitDoWhileLoop(DoWhileLoopTree node, Void p) {
        visitDoWhileLoop(node);

        return null;
    }

    protected void visitDoWhileLoop(DoWhileLoopTree node) {
        scan(node.getStatement());

        scan(node.getCondition());
    }

    public final Void visitWhileLoop(WhileLoopTree node, Void p) {
        visitWhileLoop(node);

        return null;
    }

    protected void visitWhileLoop(WhileLoopTree node) {
        scan(node.getCondition());

        scan(node.getStatement());
    }

    public final Void visitForLoop(ForLoopTree node, Void p) {
        visitForLoop(node);

        return null;
    }

    protected void visitForLoop(ForLoopTree node) {
        scan(node.getInitializer());

        scan(node.getCondition());

        scan(node.getUpdate());

        scan(node.getStatement());
    }

    public final Void visitEnhancedForLoop(EnhancedForLoopTree node, Void p) {
        visitEnhancedForLoop(node);

        return null;
    }

    protected void visitEnhancedForLoop(EnhancedForLoopTree node) {
        scan(node.getVariable());

        scan(node.getExpression());

        scan(node.getStatement());
    }

    public final Void visitLabeledStatement(LabeledStatementTree node, Void p) {
        visitLabeledStatement(node);

        return null;
    }

    protected void visitLabeledStatement(LabeledStatementTree node) {
        scan(node.getStatement());
    }

    public final Void visitSwitch(SwitchTree node, Void p) {
        visitSwitch(node);

        return null;
    }

    protected void visitSwitch(SwitchTree node) {
        scan(node.getExpression());

        scan(node.getCases());
    }

    public final Void visitCase(CaseTree node, Void p) {
        visitCase(node);

        return null;
    }

    protected void visitCase(CaseTree node) {
        scan(node.getExpression());

        scan(node.getStatements());
    }

    public final Void visitSynchronized(SynchronizedTree node, Void p) {
        visitSynchronized(node);

        return null;
    }

    protected void visitSynchronized(SynchronizedTree node) {
        scan(node.getExpression());

        scan(node.getBlock());
    }

    public final Void visitTry(TryTree node, Void p) {
        visitTry(node);

        return null;
    }

    protected void visitTry(TryTree node) {
        scan(node.getResources());

        scan(node.getBlock());

        scan(node.getCatches());

        scan(node.getFinallyBlock());
    }

    public final Void visitCatch(CatchTree node, Void p) {
        visitCatch(node);

        return null;
    }

    protected void visitCatch(CatchTree node) {
        scan(node.getParameter());

        scan(node.getBlock());
    }

    public final Void visitConditionalExpression(ConditionalExpressionTree node, Void p) {
        visitConditionalExpression(node);

        return null;
    }

    protected void visitConditionalExpression(ConditionalExpressionTree node) {
        scan(node.getCondition());

        scan(node.getTrueExpression());

        scan(node.getFalseExpression());
    }

    public final Void visitIf(IfTree node, Void p) {
        visitIf(node);

        return null;
    }

    protected void visitIf(IfTree node) {
        scan(node.getCondition());

        scan(node.getThenStatement());

        scan(node.getElseStatement());
    }

    public final Void visitExpressionStatement(ExpressionStatementTree node, Void p) {
        visitExpressionStatement(node);

        return null;
    }

    protected void visitExpressionStatement(ExpressionStatementTree node) {
        scan(node.getExpression());
    }

    public final Void visitBreak(BreakTree node, Void p) {
        visitBreak(node);

        return null;
    }

    protected void visitBreak(BreakTree node) {

    }

    public final Void visitContinue(ContinueTree node, Void p) {
        visitContinue(node);

        return null;
    }

    protected void visitContinue(ContinueTree node) {

    }

    public final Void visitReturn(ReturnTree node, Void p) {
        visitReturn(node);

        return null;
    }

    protected void visitReturn(ReturnTree node) {
        scan(node.getExpression());
    }

    public final Void visitThrow(ThrowTree node, Void p) {
        visitThrow(node);

        return null;
    }

    protected void visitThrow(ThrowTree node) {
        scan(node.getExpression());
    }

    public final Void visitAssert(AssertTree node, Void p) {
        visitAssert(node);

        return null;
    }

    protected void visitAssert(AssertTree node) {
        scan(node.getCondition());

        scan(node.getDetail());
    }

    public final Void visitMethodInvocation(MethodInvocationTree node, Void p) {
        visitMethodInvocation(node);

        return null;
    }

    protected void visitMethodInvocation(MethodInvocationTree node) {
        scan(node.getTypeArguments());

        scan(node.getMethodSelect());

        scan(node.getArguments());
    }

    public final Void visitNewClass(NewClassTree node, Void p) {
        visitNewClass(node);

        return null;
    }

    protected void visitNewClass(NewClassTree node) {
        scan(node.getEnclosingExpression());

        scan(node.getIdentifier());

        scan(node.getTypeArguments());

        scan(node.getArguments());

        scan(node.getClassBody());
    }

    public final Void visitNewArray(NewArrayTree node, Void p) {
        visitNewArray(node);

        return null;
    }

    protected void visitNewArray(NewArrayTree node) {
        scan(node.getType());

        scan(node.getDimensions());

        scan(node.getInitializers());

        scan(node.getAnnotations());

        for (Iterable< ? extends Tree> dimAnno : node.getDimAnnotations()) {
            scan(dimAnno);
        }
    }

    public final Void visitLambdaExpression(LambdaExpressionTree node, Void p) {
        visitLambdaExpression(node);

        return null;
    }

    protected void visitLambdaExpression(LambdaExpressionTree node) {
        scan(node.getParameters());

        scan(node.getBody());
    }

    public final Void visitParenthesized(ParenthesizedTree node, Void p) {
        visitParenthesized(node);

        return null;
    }

    protected void visitParenthesized(ParenthesizedTree node) {
        scan(node.getExpression());
    }

    public final Void visitAssignment(AssignmentTree node, Void p) {
        visitAssignment(node);

        return null;
    }

    protected void visitAssignment(AssignmentTree node) {
        scan(node.getVariable());

        scan(node.getExpression());
    }

    public final Void visitCompoundAssignment(CompoundAssignmentTree node, Void p) {
        visitCompoundAssignment(node);

        return null;
    }

    protected void visitCompoundAssignment(CompoundAssignmentTree node) {
        scan(node.getVariable());

        scan(node.getExpression());
    }

    public final Void visitUnary(UnaryTree node, Void p) {
        visitUnary(node);

        return null;
    }

    protected void visitUnary(UnaryTree node) {
        scan(node.getExpression());
    }

    public final Void visitBinary(BinaryTree node, Void p) {
        visitBinary(node);

        return null;
    }

    protected void visitBinary(BinaryTree node) {
        scan(node.getLeftOperand());

        scan(node.getRightOperand());
    }

    public final Void visitTypeCast(TypeCastTree node, Void p) {
        visitTypeCast(node);

        return null;
    }

    protected void visitTypeCast(TypeCastTree node) {
        scan(node.getType());

        scan(node.getExpression());
    }

    public final Void visitInstanceOf(InstanceOfTree node, Void p) {
        visitInstanceOf(node);

        return null;
    }

    protected void visitInstanceOf(InstanceOfTree node) {
        scan(node.getExpression());

        scan(node.getType());
    }

    public final Void visitArrayAccess(ArrayAccessTree node, Void p) {
        visitArrayAccess(node);

        return null;
    }

    protected void visitArrayAccess(ArrayAccessTree node) {
        scan(node.getExpression());

        scan(node.getIndex());
    }

    public final Void visitMemberSelect(MemberSelectTree node, Void p) {
        visitMemberSelect(node);

        return null;
    }

    protected void visitMemberSelect(MemberSelectTree node) {
        scan(node.getExpression());
    }

    public final Void visitMemberReference(MemberReferenceTree node, Void p) {
        visitMemberReference(node);

        return null;
    }

    protected void visitMemberReference(MemberReferenceTree node) {
        scan(node.getQualifierExpression());

        scan(node.getTypeArguments());
    }

    public final Void visitIdentifier(IdentifierTree node, Void p) {
        visitIdentifier(node);

        return null;
    }

    protected void visitIdentifier(IdentifierTree node) {

    }

    public final Void visitLiteral(LiteralTree node, Void p) {
        visitLiteral(node);

        return null;
    }

    protected void visitLiteral(LiteralTree node) {

    }

    public final Void visitPrimitiveType(PrimitiveTypeTree node, Void p) {
        visitPrimitiveType(node);

        return null;
    }

    protected void visitPrimitiveType(PrimitiveTypeTree node) {

    }

    public final Void visitArrayType(ArrayTypeTree node, Void p) {
        visitArrayType(node);

        return null;
    }

    protected void visitArrayType(ArrayTypeTree node) {
        scan(node.getType());
    }

    public final Void visitParameterizedType(ParameterizedTypeTree node, Void p) {
        visitParameterizedType(node);

        return null;
    }

    protected void visitParameterizedType(ParameterizedTypeTree node) {
        scan(node.getType());

        scan(node.getTypeArguments());
    }

    public final Void visitUnionType(UnionTypeTree node, Void p) {
        visitUnionType(node);

        return null;
    }

    protected void visitUnionType(UnionTypeTree node) {
        scan(node.getTypeAlternatives());
    }

    public final Void visitIntersectionType(IntersectionTypeTree node, Void p) {
        visitIntersectionType(node);

        return null;
    }

    protected void visitIntersectionType(IntersectionTypeTree node) {
        scan(node.getBounds());
    }

    public final Void visitTypeParameter(TypeParameterTree node, Void p) {
        visitTypeParameter(node);

        return null;
    }

    protected void visitTypeParameter(TypeParameterTree node) {
        scan(node.getAnnotations());

        scan(node.getBounds());
    }

    public final Void visitWildcard(WildcardTree node, Void p) {
        visitWildcard(node);

        return null;
    }

    protected void visitWildcard(WildcardTree node) {
        scan(node.getBound());
    }

    public final Void visitModifiers(ModifiersTree node, Void p) {
        visitModifiers(node);

        return null;
    }

    protected void visitModifiers(ModifiersTree node) {
        scan(node.getAnnotations());
    }

    public final Void visitAnnotation(AnnotationTree node, Void p) {
        visitAnnotation(node);

        return null;
    }

    protected void visitAnnotation(AnnotationTree node) {
        scan(node.getAnnotationType());

        scan(node.getArguments());
    }

    public final Void visitAnnotatedType(AnnotatedTypeTree node, Void p) {
        visitAnnotatedType(node);

        return null;
    }

    protected void visitAnnotatedType(AnnotatedTypeTree node) {
        scan(node.getAnnotations());

        scan(node.getUnderlyingType());
    }

    public final Void visitOther(Tree node, Void p) {
        visitOther(node);

        return null;
    }

    private void visitOther(Tree node) {

    }

    public final Void visitErroneous(ErroneousTree node, Void p) {
        visitErroneous(node);

        return null;
    }

    private void visitErroneous(ErroneousTree node) {
        scan(node.getErrorTrees());
    }
}

