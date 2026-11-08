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
  public String post(String platform, SocialProperties.Account account, String contentId, String text) {
    return deliver(platform,account,contentId,text,true);
  }
  public String send(String platform, SocialProperties.Account account, String parentId, String text) {
    return deliver(platform,account,parentId,text,false);
  }
  private String deliver(String platform, SocialProperties.Account account, String parentId, String text, boolean root) {
    if(!supports(platform)) throw new PublishFailure("FAILED","Unsupported comment platform");
    var config=properties.getPlatforms().get(platform);
    String base=config.getBaseUrl().replaceAll("/+$", "");
    String token=tokens.token(platform,account);
    PublishingHttp.Response response;
    try {
    if(platform.equals("youtube")) {
      response=http.json(base+(root ? "/commentThreads" : "/comments")+"?part=snippet",token,
          root ? Map.of("snippet",Map.of("channelId",account.getExternalId(),"videoId",parentId,"topLevelComment",Map.of("snippet",Map.of("textOriginal",text))))
              : Map.of("snippet",Map.of("parentId",parentId,"textOriginal",text)),Map.of());
    } else {
      response=http.form(base+"/"+config.getApiVersion()+"/"+PublishingHttp.encode(parentId)+
          (platform.equals("instagram") && !root?"/replies":"/comments"),token,Map.of("message",text),true);
    }
    } catch(PublishFailure failure) {
      if(failure.status().equals("ACTION_REQUIRED")) tokens.invalidate(platform,account.getExternalId());
      throw failure;
    }
    String id=platform.equals("youtube") && root
        ? response.body().path("snippet").path("topLevelComment").path("id").asText()
        : response.body().path("id").asText();
    if(id.isBlank() || id.length()>191) throw new PublishFailure("UNKNOWN","Provider did not return a valid reply ID; inspect the thread");
    return id;
  }
}
