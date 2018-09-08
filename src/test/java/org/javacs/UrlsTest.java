package org.javacs;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import java.net.URL;
import org.junit.Test;

public class UrlsTest {

  @Test
  public void of_whenPathStartsWithForwardSlash() throws Exception {
    URL actual = Urls.of("/a/b/c");
    assertThat(actual.getProtocol(), equalTo("file"));
    assertThat(actual.getPath(), containsString("/a/b/c"));
  }

  @Test
  public void of_whenPathStartsWithProtocol() throws Exception {
    URL actual = Urls.of("file:///a/b/c");
    assertThat("file", equalTo(actual.getProtocol()));
    assertThat(actual.getPath(), containsString("/a/b/c"));
  }

  @Test
  public void of_whenPathStartsWithDriveLetter_usingForwardSlashes()
      throws Exception {
    URL actual = Urls.of("c:/a/b/c");
    assertThat(actual.getProtocol(), equalTo("file"));
    assertThat(actual.getPath(), containsString("/a/b/c"));
  }

  @Test
  public void of_whenPathStartsWithDriveLetter_usingBackslashes()
      throws Exception {
    URL actual = Urls.of("c:\\a\\b\\c");
    assertThat(actual.getProtocol(), equalTo("file"));
    assertThat(actual.getPath(), containsString("/a/b/c"));
  }
}