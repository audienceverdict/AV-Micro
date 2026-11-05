package com.slokam.social.replies;
import java.util.*;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;
@Data @Component @ConfigurationProperties("social.replies")
public class ReplyProperties {
  private boolean enabled = true;
  private Map<String, Operator> operators = new LinkedHashMap<>();
  @Data public static class Operator {
    // Explicit local social_accounts IDs; an empty list grants no access.
    private List<Long> accountIds = new ArrayList<>();
  }
}
