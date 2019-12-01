package org.javacs.hover;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import java.util.List;
import java.util.StringJoiner;
import org.javacs.lsp.MarkupContent;
import org.javacs.lsp.MarkupKind;

// TODO this should not be public once completion process is refactored
public class MarkdownHelper {

    public static String asMarkdown(List<? extends DocTree> lines) {
        var join = new StringJoiner("\n");
        for (var l : lines) join.add(l.toString());
        var html = join.toString();
        return TipFormatter.asMarkdown(html);
    }

    public static String asMarkdown(DocCommentTree comment) {
        var lines = comment.getFirstSentence();
        return asMarkdown(lines);
    }

    public static MarkupContent asMarkupContent(DocCommentTree comment) {
        var markdown = asMarkdown(comment);
        var content = new MarkupContent();
        content.kind = MarkupKind.Markdown;
        content.value = markdown;
        return content;
    }
}
