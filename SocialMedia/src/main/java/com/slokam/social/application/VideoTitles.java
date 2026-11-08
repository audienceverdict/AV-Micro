package com.slokam.social.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared matching rules for manual creation, synchronization and data migration. */
public final class VideoTitles {
  private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

  private VideoTitles() {}

  public static String normalize(String title) {
    if (title == null) return "";
    return WHITESPACE.matcher(Normalizer.normalize(title, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)).replaceAll("");
  }

  public static String key(String title) {
    String normalized = normalize(title);
    if (normalized.isEmpty()) return null;
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(normalized.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Captions have no separate title; use their first nonempty line without truncating it. */
  public static String displayTitle(String title, String caption) {
    if (!normalize(title).isEmpty()) return title;
    if (caption == null) return null;
    return caption.lines().filter(line -> !normalize(line).isEmpty())
        .findFirst().map(String::strip).orElse(null);
  }

  public static boolean isVideo(String type) {
    return "VIDEO".equals(type) || "SHORT_VIDEO".equals(type)
        || "REEL".equals(type) || "LIVE".equals(type);
  }
}
