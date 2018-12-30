package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.Test;

public class StringSearchTest {
    private void testNext(String pat, String text, int index) {
        var got = new StringSearch(pat).next(text);
        assertThat(got, equalTo(index));
    }

    private void testNextWord(String pat, String text, int index) {
        var got = new StringSearch(pat).nextWord(text);
        assertThat(got, equalTo(index));
    }

    @Test
    public void testNext() {
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

    @Test
    public void testNextWord() {
        testNextWord("", "", 0);
        testNextWord("", "abc", -1);
        testNextWord("abc", "", -1);
        testNextWord("abc", "abc", 0);
        testNextWord("d", "abcdefg", -1);
        testNextWord("d", "abc d efg", 4);
        testNextWord("nan", "banana", -1);
        testNextWord("nan", "ba nan a", 3);
        testNextWord("abcd", "abc", -1);
        testNextWord("abcd", "bcd", -1);
        testNextWord("bcd", "abcd", -1);
        testNextWord("bcd", "a bcd", 2);
        testNextWord("abc", "abc d", 0);
        testNextWord("aa", "aaa", -1);
        testNextWord("aa", "a aa", 2);
        testNextWord("aa", "aa a", 0);
    }
}
