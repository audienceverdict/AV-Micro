package com.slokam.social.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("social")
public class SocialProperties {
  @Valid private Sync sync = new Sync();
  @Valid private Http http = new Http();
  @Valid private Statistics statistics = new Statistics();
  @Valid private Map<String, Platform> platforms = new LinkedHashMap<>();

  @Data
  public static class Sync {
    private boolean enabled = true;

    @Min(1)
    private long intervalMs = 300000;

    @Min(0)
    private long initialDelayMs = 0;

    @Min(0)
    private long overlapSeconds = 120;

    @Min(1)
    @Max(100)
    private int pageSize = 100;

    @Min(1)
    private int backfillBatchPages = 10;

    @Min(1)
    private int incrementalBatchPages = 10;

    @Min(1)
    private long reconciliationIntervalSeconds = 86400;
  }

  @Data
  public static class Http {
    @Min(1)
    private int connectTimeoutMs = 5000;

    @Min(1)
    private int readTimeoutSeconds = 30;

    @Min(1)
    private int maxAttempts = 4;

    @Min(1)
    private long retryBaseMs = 500;

    @Min(1)
    private long maxRetryAfterSeconds = 300;

    @Min(1)
    private int requestsPerMinute = 60;
  }

  @Data
  public static class Platform {
    private boolean enabled = true;
    private String baseUrl;
    private String apiVersion = "v26.0";
    @Valid private List<Account> accounts = new ArrayList<>();
  }

  @Data
  public static class Account {
    @NotBlank private String externalId;
    private String name;
    @NotBlank private String credentialReference;
    private String apiKeyReference;
    // Optional per-channel offline OAuth token for publishing.
    private String refreshTokenReference;
    private boolean publishingEnabled = true;
    private boolean commentRepliesEnabled = false;
    private boolean enabled = true;
    private boolean storiesEnabled = false;
    private boolean engagementEnabled = false;
    private boolean includePlaylists = true;
    private boolean includePhotos = true;
    private boolean statisticsEnabled = true;
  }

  @Data
  public static class Statistics {
    private boolean enabled = true;
    @Min(1) private int requestsPerAccount = 20;
    @Min(1) private long refreshSeconds = 3600;
    @Min(1) private long failureRetrySeconds = 86400;
    @Min(1) private int snapshotSeconds = 86400;
    @Min(1) @Max(365) private int lookbackDays = 28;
    @Min(1) @Max(100) private int maxPages = 100;
    private Map<String, YoutubeReport> youtubeReports = defaultYoutubeReports();
    @Valid private Map<String, InsightReport> insightReports = new LinkedHashMap<>();
    private static Map<String, YoutubeReport> defaultYoutubeReports() {
      var reports = new LinkedHashMap<String, YoutubeReport>();
      reports.put("demographics", new YoutubeReport("ageGroup,gender", "viewerPercentage", false));
      reports.put("traffic", new YoutubeReport("insightTrafficSourceType", "views,estimatedMinutesWatched", false));
      reports.put("geography", new YoutubeReport("country", "views,estimatedMinutesWatched", false));
      reports.put("devices", new YoutubeReport("deviceType", "views,estimatedMinutesWatched", false));
      reports.put("operating-systems", new YoutubeReport("operatingSystem", "views,estimatedMinutesWatched", false));
      reports.put("playback-location", new YoutubeReport("insightPlaybackLocationType", "views,estimatedMinutesWatched", false));
      reports.put("sharing", new YoutubeReport("sharingService", "shares", false));
      reports.put("subscription", new YoutubeReport("subscribedStatus", "views,estimatedMinutesWatched", false));
      reports.put("retention", new YoutubeReport("elapsedVideoTimeRatio", "audienceWatchRatio,relativeRetentionPerformance", true));
      return reports;
    }
    // Provider metric lists are configurable because versions and media types differ.
    private List<String> instagramMediaMetrics = new ArrayList<>(List.of(
        "views", "reach", "likes", "comments", "saved", "shares", "total_interactions",
        "follows", "profile_visits", "profile_activity", "navigation",
        "ig_reels_avg_watch_time", "ig_reels_video_view_total_time", "reposts"));
    private List<String> instagramAccountMetrics = new ArrayList<>(List.of(
        "views", "reach", "accounts_engaged", "total_interactions", "likes", "comments",
        "shares", "saves", "replies", "follows_and_unfollows", "profile_links_taps"));
    private List<String> facebookPostMetrics = new ArrayList<>(List.of(
        "post_media_view", "post_total_media_view_unique", "post_clicks",
        "post_clicks_by_type", "post_reactions_by_type_total"));
    private List<String> facebookVideoMetrics = new ArrayList<>(List.of(
        "total_video_views", "total_video_views_unique", "total_video_avg_time_watched",
        "total_video_view_total_time", "total_video_complete_views",
        "total_video_10s_views", "total_video_30s_views", "total_video_views_paid",
        "total_video_views_organic", "total_video_retention_graph"));
    private List<String> facebookAccountMetrics = new ArrayList<>(List.of(
        "page_media_view", "page_total_media_view_unique", "page_post_engagements",
        "page_daily_follows", "page_daily_unfollows", "page_follows"));
    private List<String> youtubeAnalyticsMetrics = new ArrayList<>(List.of(
        "views", "engagedViews", "likes", "dislikes", "comments", "shares",
        "estimatedMinutesWatched", "averageViewDuration", "averageViewPercentage",
        "subscribersGained", "subscribersLost", "videosAddedToPlaylists",
        "videosRemovedFromPlaylists", "cardImpressions", "cardClicks", "cardClickRate",
        "cardTeaserImpressions", "cardTeaserClicks", "cardTeaserClickRate",
        "estimatedRevenue", "estimatedAdRevenue", "estimatedRedPartnerRevenue",
        "grossRevenue", "monetizedPlaybacks", "playbackBasedCpm", "adImpressions", "cpm"));
  }

  @Data
  @lombok.NoArgsConstructor
  @lombok.AllArgsConstructor
  public static class YoutubeReport {
    private String dimensions;
    private String metrics;
    private boolean contentOnly;
  }

  /** Additional version-specific insight requests, including demographic breakdowns. */
  @Data
  public static class InsightReport {
    @NotBlank private String platform;
    @Pattern(regexp = "account|content") private String scope = "account";
    @NotBlank private String metric;
    private String period;
    private String metricType;
    private String breakdown;
    private String timeframe;
  }
}
