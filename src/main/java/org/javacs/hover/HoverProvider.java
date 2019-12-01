package org.javacs.hover;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Logger;
import javax.lang.model.element.*;
import org.javacs.CompilerProvider;
import org.javacs.FindHelper;
import org.javacs.MarkdownHelper;
import org.javacs.ParseTask;
import org.javacs.lsp.MarkedString;

public class HoverProvider {
    final CompilerProvider compiler;

    public static final List<MarkedString> NOT_SUPPORTED = List.of();

    public HoverProvider(CompilerProvider compiler) {
        this.compiler = compiler;
    }

    public List<MarkedString> hover(Path file, int line, int column) {
        try (var task = compiler.compile(file)) {
            var position = task.root().getLineMap().getPosition(line, column);
            var element = new FindHoverElement(task.task).scan(task.root(), position);
            if (element == null) return NOT_SUPPORTED;
            var list = new ArrayList<MarkedString>();
            var code = printType(element);
            list.add(new MarkedString("java", code));
            var docs = docs(element);
            if (!docs.isEmpty()) {
                list.add(new MarkedString(docs));
            }
            return list;
        }
    }

    private String docs(Element element) {
        var className = className(element);
        if (className.isEmpty()) return "";
        var toFile = compiler.findAnywhere(className);
        if (toFile.isEmpty()) return "";
        var task = compiler.parse(toFile.get());
        var tree = find(task, element);
        if (tree == null) return "";
        var path = Trees.instance(task.task).getPath(task.root, tree);
        var docTree = DocTrees.instance(task.task).getDocCommentTree(path);
        if (docTree == null) return "";
        return MarkdownHelper.asMarkdown(docTree);
    }

    private Tree find(ParseTask task, Element element) {
        switch (element.getKind()) {
            case FIELD:
                return FindHelper.findField(task, element);
            case METHOD:
            case CONSTRUCTOR:
                return FindHelper.findMethod(task, (ExecutableElement) element);
            case INTERFACE:
            case CLASS:
            case ENUM:
                return FindHelper.findType(task, (TypeElement) element);
            default:
                return null;
        }
    }

    // TODO this should parameterize the type
    // TODO show more information about declarations---was this a parameter, a field? What were the modifiers?
    private String printType(Element e) {
        if (e instanceof ExecutableElement) {
            var m = (ExecutableElement) e;
            return ShortTypePrinter.DEFAULT.printMethod(m);
        } else if (e instanceof VariableElement) {
            var v = (VariableElement) e;
            return ShortTypePrinter.DEFAULT.print(v.asType()) + " " + v;
        } else if (e instanceof TypeElement) {
            var t = (TypeElement) e;
            var lines = new StringJoiner("\n");
            lines.add(hoverTypeDeclaration(t) + " {");
            for (var member : t.getEnclosedElements()) {
                // TODO check accessibility
                if (member instanceof ExecutableElement || member instanceof VariableElement) {
                    lines.add("  " + printType(member) + ";");
                } else if (member instanceof TypeElement) {
                    lines.add("  " + hoverTypeDeclaration((TypeElement) member) + " { /* removed */ }");
                }
            }
            lines.add("}");
            return lines.toString();
        } else {
            return e.toString();
        }
    }

    private String className(Element e) {
        while (e != null) {
            if (e instanceof TypeElement) {
                var type = (TypeElement) e;
                return type.getQualifiedName().toString();
            }
            e = e.getEnclosingElement();
        }
        return "";
    }

    private String hoverTypeDeclaration(TypeElement t) {
        var result = new StringBuilder();
        switch (t.getKind()) {
            case ANNOTATION_TYPE:
                result.append("@interface");
                break;
            case INTERFACE:
                result.append("interface");
                break;
            case CLASS:
                result.append("class");
                break;
            case ENUM:
                result.append("enum");
                break;
            default:
                LOG.warning("Don't know what to call type element " + t);
                result.append("_");
        }
        result.append(" ").append(ShortTypePrinter.DEFAULT.print(t.asType()));
        var superType = ShortTypePrinter.DEFAULT.print(t.getSuperclass());
        switch (superType) {
            case "Object":
            case "none":
                break;
            default:
                result.append(" extends ").append(superType);
        }
        return result.toString();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
