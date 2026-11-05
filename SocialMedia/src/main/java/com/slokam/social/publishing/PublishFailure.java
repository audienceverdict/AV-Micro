package com.slokam.social.publishing;

/** Safe messages only: never include remote bodies, session URLs or tokens. */
public class PublishFailure extends RuntimeException {
  private final String status;
  private final long retrySeconds;
  public PublishFailure(String status, String message) {
    this(status, message, 300);
  }
  public PublishFailure(String status, String message, long retrySeconds) {
    super(message);
    this.status = status;
    this.retrySeconds = retrySeconds;
  }
  public long retrySeconds() { return retrySeconds; }
  public String status() { return status; }
}
