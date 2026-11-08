package com.slokam.social.moderation;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record PostCategory(Topic topic, String movieName, String applicationVideoName) {
  public enum Topic { MOVIE, AV }

  public void validate() {
    if (topic == null) throw invalid("Choose MOVIE or AV");
    String name = topic == Topic.MOVIE ? movieName : applicationVideoName;
    String other = topic == Topic.MOVIE ? applicationVideoName : movieName;
    if (name == null || name.isBlank() || name.trim().length() > 255)
      throw invalid(topic == Topic.MOVIE ? "Movie name is required (max 255 characters)"
          : "Application video name is required (max 255 characters)");
    if (other != null && !other.isBlank()) throw invalid("Supply only the name for the selected topic");
  }

  public String name() { return (topic == Topic.MOVIE ? movieName : applicationVideoName).trim(); }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
