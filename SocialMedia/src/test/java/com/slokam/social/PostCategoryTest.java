package com.slokam.social;

import com.slokam.social.moderation.PostCategory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;

class PostCategoryTest {
  @Test void requiresTheSelectedTopicsName() {
    assertThrows(ResponseStatusException.class, () -> new PostCategory(null, null, null).validate());
    assertThrows(ResponseStatusException.class, () -> new PostCategory(PostCategory.Topic.MOVIE, " ", null).validate());
    assertThrows(ResponseStatusException.class, () -> new PostCategory(PostCategory.Topic.AV, "Movie", null).validate());
    assertThrows(ResponseStatusException.class, () -> new PostCategory(PostCategory.Topic.AV, "Movie", "AV").validate());
    var valid = new PostCategory(PostCategory.Topic.MOVIE, " Movie ", null);
    valid.validate();
    assertEquals("Movie", valid.name());
  }
}
