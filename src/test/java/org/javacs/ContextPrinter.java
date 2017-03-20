package org.javacs;

import com.sun.tools.javac.util.Context;

import java.lang.reflect.Field;
import java.util.*;

public class ContextPrinter {
    public static Tree tree(Context context, int depth) throws NoSuchFieldException, IllegalAccessException {
        Field ht = Context.class.getDeclaredField("ht");

        ht.setAccessible(true);

        Map<Context.Key<?>,Object> map = (Map<Context.Key<?>, Object>) ht.get(context);
        Node root = Node.anonymous();
        TreeConverter treeConverter = new TreeConverter();

        treeConverter.seen.add(context);
        map.values().forEach(treeConverter.seen::add);

        for (Context.Key<?> key : map.keySet()) {
            Object value = map.get(key);

            treeConverter.seen.remove(value);

            root.children.put(value.getClass().getSimpleName(), treeConverter.convert(value, depth));
        }

        return root;
    }
}

class TreeConverter {
    Set<Object> seen = new HashSet<>();

    Tree convert(Object o, int depth) throws IllegalAccessException {
        if (o == null)
            return new Leaf("null");
        else if (seen.contains(o))
            return new Leaf("<" + o.getClass().getSimpleName() + ">");
        else {
            seen.add(o);

            if (o instanceof Map) {
                Node node = Node.anonymous();
                Map map = (Map) o;

                for (Object key : map.keySet()) {
                    Object value = map.get(key);

                    node.children.put(keyString(key, value, node.children.keySet()), convert(value, depth));
                }

                return node;
            } else if (o instanceof Iterable) {
                Seq seq = new Seq();
                Iterable it = (Iterable) o;

                for (Object each : it)
                    seq.children.add(convert(each, depth));

                return seq;
            } else if (depth > 0) {
                Class<?> klass = o.getClass();
                Node node = Node.named(klass.getSimpleName());

                for (Field field : klass.getDeclaredFields()) {
                    field.setAccessible(true);

                    Object value = field.get(o);

                    node.children.put(field.getName(), convert(value, depth - 1));
                }

                return node;
            }
            else return new Leaf("<" + o.getClass().getSimpleName() + ">");
        }
    }

    private String keyString(Object key, Object value, Set<String> used) {
        if (key instanceof Context.Key)
            return "Context.Key<" + value.getClass().getSimpleName() + ">";
        else
            return key.toString();
    }
}

class IndentPrinter {
    private final StringBuilder out = new StringBuilder();
    private int indent = 0;
    private boolean startOfLine = true;

    IndentPrinter increaseIndent() {
        indent++;

        return this;
    }

    IndentPrinter decreaseIndent() {
        indent--;

        return this;
    }

    IndentPrinter append(String content) {
        if (startOfLine) {
            for (int i = 0; i < indent; i++)
                out.append("  ");

            startOfLine = false;
        }

        out.append(content);

        return this;
    }

    IndentPrinter newline() {
        out.append("\n");
        startOfLine = true;

        return this;
    }

    @Override
    public String toString() {
        return out.toString();
    }
}

abstract class Tree {
    protected abstract void print(IndentPrinter out);

    @Override
    public String toString() {
        IndentPrinter printer = new IndentPrinter();

        print(printer);

        return printer.toString();
    }
}

class Node extends Tree {
    final Optional<String> name;
    final Map<String, Tree> children = new HashMap<>();

    private Node(Optional<String> name) {
        this.name = name;
    }

    static Node anonymous() {
        return new Node(Optional.empty());
    }

    static Node named(String name) {
        return new Node(Optional.of(name));
    }

    @Override
    public void print(IndentPrinter out) {
        name.ifPresent(out::append);

        out.append("{").newline().increaseIndent();

        children.forEach((key, value) -> {
            out.append(key).append(": ");
            value.print(out);
            out.newline();
        });

        out.decreaseIndent().append("}");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return Objects.equals(name, node.name) &&
                Objects.equals(children, node.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, children);
    }
}

class Seq extends Tree {
    final List<Tree> children = new ArrayList<>();

    @Override
    public void print(IndentPrinter out) {
        out.append("[").newline().increaseIndent();

        children.forEach(child -> child.print(out));

        out.decreaseIndent().append("]");
    }
}

class Leaf extends Tree {
    final String value;

    Leaf(String value) {
        this.value = value;
    }

    @Override
    public void print(IndentPrinter out) {
        out.append(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Leaf leaf = (Leaf) o;
        return Objects.equals(value, leaf.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}