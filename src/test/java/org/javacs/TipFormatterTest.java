package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class TipFormatterTest {
    @Test
    public void formatSimpleTags() {
        assertThat(TipFormatter.asMarkdown("<i>foo</i>"), equalTo("*foo*"));
        assertThat(TipFormatter.asMarkdown("<b>foo</b>"), equalTo("**foo**"));
        assertThat(TipFormatter.asMarkdown("<pre>foo</pre>"), equalTo("`foo`"));
        assertThat(TipFormatter.asMarkdown("<code>foo</code>"), equalTo("`foo`"));
        assertThat(TipFormatter.asMarkdown("{@code foo}"), equalTo("`foo`"));
    }

    @Test
    public void formatMultipleTags() {
        assertThat(TipFormatter.asMarkdown("<code>foo</code> <code>bar</code>"), equalTo("`foo` `bar`"));
        assertThat(TipFormatter.asMarkdown("{@code foo} {@code bar}"), equalTo("`foo` `bar`"));
    }
}
