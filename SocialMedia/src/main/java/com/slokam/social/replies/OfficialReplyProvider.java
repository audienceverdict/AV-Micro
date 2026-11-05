package com.slokam.social.replies;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.publishing.*;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class OfficialReplyProvider implements CommentReplyProvider {
  private final SocialProperties properties;
  private final PublishingHttp http;
  private final PublishingTokens tokens;
  public OfficialReplyProvider(SocialProperties properties, PublishingHttp http, PublishingTokens tokens) {
    this.properties=properties; this.http=http; this.tokens=tokens;
  }
  public boolean supports(String platform) { return Set.of("youtube","facebook","instagram").contains(platform); }
  public String send(String platform, SocialProperties.Account account, String parentId, String text) {
    if(!supports(platform)) throw new PublishFailure("FAILED","Unsupported comment platform");
    var config=properties.getPlatforms().get(platform);
    String base=config.getBaseUrl().replaceAll("/+$", "");
    String token=tokens.token(platform,account);
    PublishingHttp.Response response;
    try {
    if(platform.equals("youtube")) {
      response=http.json(base+"/comments?part=snippet",token,
          Map.of("snippet",Map.of("parentId",parentId,"textOriginal",text)),Map.of());
    } else {
      response=http.form(base+"/"+config.getApiVersion()+"/"+PublishingHttp.encode(parentId)+
          (platform.equals("instagram")?"/replies":"/comments"),token,Map.of("message",text),true);
    }
    } catch(PublishFailure failure) {
      if(failure.status().equals("ACTION_REQUIRED")) tokens.invalidate(platform,account.getExternalId());
      throw failure;
    }
    String id=response.body().path("id").asText();
    if(id.isBlank() || id.length()>191) throw new PublishFailure("UNKNOWN","Provider did not return a valid reply ID; inspect the thread");
    return id;
  }
}
