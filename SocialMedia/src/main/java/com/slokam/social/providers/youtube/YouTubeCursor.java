package com.slokam.social.providers.youtube;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;

record YouTubeCursor(String stage, String page, int index, String items) {
  static YouTubeCursor decode(String raw, ObjectMapper mapper) {
    if (raw == null) return new YouTubeCursor("uploads", null, 0, null);
    try {
      return mapper.readValue(Base64.getUrlDecoder().decode(raw), YouTubeCursor.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid YouTube continuation");
    }
  }

  String encode(ObjectMapper mapper) {
    try {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(this));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot encode continuation");
    }
  }
}
