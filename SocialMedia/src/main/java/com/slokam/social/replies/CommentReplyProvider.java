package com.slokam.social.replies;
import com.slokam.social.configuration.SocialProperties;
public interface CommentReplyProvider {
  boolean supports(String platform);
  default String post(String platform, SocialProperties.Account account, String contentId, String text) {
    throw new com.slokam.social.publishing.PublishFailure("FAILED", "Top-level comments unsupported");
  }
  String send(String platform, SocialProperties.Account account, String parentId, String text);
}
