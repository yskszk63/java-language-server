package org.javacs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.net.URL;
import org.junit.Test;

public class UrlsTest {

  @Test
  public void of_whenPathStartsWithSlash() throws Exception {
    URL actual = Urls.of("file:///a/b/c");
    assertEquals("file", actual.getProtocol());
    assertEquals("/a/b/c", actual.getPath());
  }

  @Test
  public void of_whenPathStartsWithProtocol() throws Exception {
    URL actual = Urls.of("file:///a/b/c");
    assertEquals("file", actual.getProtocol());
    assertEquals("/a/b/c", actual.getPath());
  }

  @Test
  public void of_whenPathStartsWithDriveLetter() throws Exception {
    URL actual = Urls.of("c:/a/b/c");
    assertEquals("file", actual.getProtocol());
  }

  @Test
  public void isSystemPath_whenPathStartsWithSlash() {
    assertTrue(Urls.isSystemPath("/a/b/c"));
  }

  @Test
  public void isSystemPath_whenPathStartsWithDriveLetter() {
    assertTrue(Urls.isSystemPath("c:/a/b/c"));
    assertTrue(Urls.isSystemPath("c:\\a\\b\\c"));
  }

  @Test
  public void isSystemPath_whenPathStartsWithProtocol() {
    assertFalse(Urls.isSystemPath("file://a/b/c"));
  }
}