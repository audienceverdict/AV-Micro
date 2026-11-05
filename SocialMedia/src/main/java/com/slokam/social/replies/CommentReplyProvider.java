package com.slokam.social.replies;
import com.slokam.social.configuration.SocialProperties;
public interface CommentReplyProvider {
  boolean supports(String platform);
  String send(String platform, SocialProperties.Account account, String parentId, String text);
}
