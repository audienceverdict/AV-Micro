package com.slokam.social.core.exception;

import java.time.Duration;

public class ProviderException extends RuntimeException {
  private final boolean retryable;
  private final Duration retryAfter;
  private final String category;

  public ProviderException(String safeMessage, boolean retryable, Duration retryAfter) {
    this(safeMessage, retryable, retryAfter, "FAILED");
  }

  public ProviderException(String safeMessage, boolean retryable, Duration retryAfter, String category) {
    super(safeMessage);
    this.retryable = retryable;
    this.retryAfter = retryAfter;
    this.category = category;
  }

  public boolean retryable() {
    return retryable;
  }

  public Duration retryAfter() {
    return retryAfter;
  }

  public String category() { return category; }
}
