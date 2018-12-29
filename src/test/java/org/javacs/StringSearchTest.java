package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class StringSearchTest {
    private void testNext(String pat, String text, int index) {
        var got = new StringSearch(pat).next(text);
        assertThat(got, equalTo(index));
    }

    @Test
    public void testFinderNext() {
        testNext("", "", 0);
        testNext("", "abc", 0);
        testNext("abc", "", -1);
        testNext("abc", "abc", 0);
        testNext("d", "abcdefg", 3);
        testNext("nan", "banana", 2);
        testNext("pan", "anpanman", 2);
        testNext("nnaaman", "anpanmanam", -1);
        testNext("abcd", "abc", -1);
        testNext("abcd", "bcd", -1);
        testNext("bcd", "abcd", 1);
        testNext("abc", "acca", -1);
        testNext("aa", "aaa", 0);
        testNext("baa", "aaaaa", -1);
        testNext("at that", "which finally halts.  at that point", 22);
    }
}
