package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;

import com.slokam.social.application.VideoTitles;
import org.junit.jupiter.api.Test;

class VideoTitlesTest {
  @Test
  void ignoresCaseAndAllUnicodeWhitespace() {
    assertEquals(VideoTitles.key("HI Hello"), VideoTitles.key("  hi     HELLO\t\n"));
    assertEquals(VideoTitles.key("Antahkarana"), VideoTitles.key("Antah karana"));
    assertEquals(VideoTitles.key("Antahkarana"), VideoTitles.key("Antah\u00a0\u2003\u0085karana"));
    assertEquals(VideoTitles.key("Café"), VideoTitles.key("CAFE\u0301"));
    assertNotEquals(VideoTitles.key("Antahkarana"), VideoTitles.key("Antah-karana"));
    assertNull(VideoTitles.key(null));
    assertNull(VideoTitles.key(" \t\n\u00a0"));
  }

  @Test
  void keepsExplicitTitleOrUsesFirstNonemptyCaptionLine() {
    assertEquals(" HI Hello ", VideoTitles.displayTitle(" HI Hello ", "Other caption"));
    assertEquals("Antah karana", VideoTitles.displayTitle(null, "\n \nAntah karana\nDescription #tag"));
    assertEquals("Caption", VideoTitles.displayTitle("\u00a0", "Caption"));
    assertNull(VideoTitles.displayTitle(null, " \n\t"));
  }
}
