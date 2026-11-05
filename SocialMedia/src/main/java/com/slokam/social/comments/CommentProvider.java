package com.slokam.social.comments;

import com.slokam.social.core.model.SocialAccount;

public interface CommentProvider {
  boolean supports(String platform);

  CommentPage fetch(
      SocialAccount account, String contentId, String parentId, String cursor, int size);
}
