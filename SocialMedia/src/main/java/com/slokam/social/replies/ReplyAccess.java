package com.slokam.social.replies;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Component
public class ReplyAccess {
  public record Actor(String name, List<Long> accounts) {
    public void require(long id) {
      if (!accounts.contains(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account access denied");
    }
  }
  private final ReplyProperties properties;
  public ReplyAccess(ReplyProperties properties) { this.properties=properties; }
  public Actor authenticate(HttpServletRequest request) {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt)
        || !jwt.isAuthenticated())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Valid Identity access token required");
    if (jwt.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator access required");
    var operator = properties.getOperators().get(jwt.getName());
    return new Actor(jwt.getName(), operator == null ? List.of() : List.copyOf(operator.getAccountIds()));
  }
}
