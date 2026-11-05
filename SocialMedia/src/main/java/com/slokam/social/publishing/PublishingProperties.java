package com.slokam.social.publishing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.file.Path;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties("social.publishing")
public class PublishingProperties {
  private boolean enabled = true;
  private Path storageDirectory = Path.of("data/publishing");
  // Public HTTPS origin/path pointing to this app, required for Meta media fetches.
  private String publicBaseUrl = "";
  private String publicMediaPath = "/api/content/";
  @Min(1) private int batchSize = 10;
  @Min(1) private long maxMediaBytes = 536870912;
  @Min(10) @Max(900) private int requestTimeoutSeconds = 300;
  @Valid private Youtube youtube = new Youtube();
  @Valid private Meta meta = new Meta();

  @Data
  public static class Youtube {
    private String clientId = "";
    private String clientSecret = "";
    private String tokenUrl = "https://oauth2.googleapis.com/token";
    private String uploadUrl = "https://www.googleapis.com/upload/youtube/v3/videos";
  }

  @Data
  public static class Meta {
    private String clientId = "";
    private String clientSecret = "";
  }
}
