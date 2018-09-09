package org.javacs;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.net.URL;
import org.junit.Test;

public class UrlsTest {
    
    @Test
    public void pathToUrl_whenPathStartsWithForwardSlash() throws Exception {
        URL actual = Urls.pathToUrl("/a/b/c");
        assertThat(actual.getProtocol(), equalTo("file"));
    }
    
    @Test
    public void pathToUrl_whenPathStartsWithProtocol() throws Exception {
        URL actual = Urls.pathToUrl("file:///a/b/c");
        assertThat(actual.getProtocol(), equalTo("file"));
    }
    
    @Test
    public void pathToUrl_whenPathStartsWithDriveLetter_usingForwardSlashes()
            throws Exception {
        URL actual = Urls.pathToUrl("c:/a/b/c");
        assertThat(actual.getProtocol(), equalTo("file"));
    }
    
    @Test
    public void pathToUrl_whenPathStartsWithDriveLetter_usingBackslashes()
            throws Exception {
        URL actual = Urls.pathToUrl("c:\\a\\b\\c");
        assertThat(actual.getProtocol(), equalTo("file"));
    }
}
