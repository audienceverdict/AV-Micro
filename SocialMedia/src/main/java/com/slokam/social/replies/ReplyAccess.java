package com.slokam.social.replies;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Component
public class ReplyAccess {
  public record Actor(String name, List<Long> accounts, boolean allAccounts) {
    public Actor(String name, List<Long> accounts) { this(name,accounts,false); }
    public void require(long id) {
      if (!allAccounts && !accounts.contains(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account access denied");
    }
  }
  private final ReplyProperties properties;
  public ReplyAccess(ReplyProperties properties) { this.properties=properties; }
  public String reader() {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt)
        || !jwt.isAuthenticated())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Valid Identity access token required");
    return jwt.getName();
  }
  public Actor authenticate(HttpServletRequest request) {
    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt)
        || !jwt.isAuthenticated())
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Valid Identity access token required");
    if (jwt.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator access required");
    return new Actor(jwt.getName(), List.of(), true);
  }
}
