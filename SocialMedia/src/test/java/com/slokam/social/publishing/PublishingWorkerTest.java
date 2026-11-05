package com.slokam.social.publishing;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PublishingWorkerTest {
  @Test void ambiguousPostIsHeldButSavedYoutubeSessionCanRecover() {
    for (String platform : new String[]{"facebook", "youtube"}) {
      var jdbc = mock(JdbcTemplate.class);
      var publisher = mock(SocialPublisher.class);
      var worker = spy(new PublishingWorker(jdbc, mock(TransactionTemplate.class), new PublishingProperties(), publisher, mock(PublishingTokens.class)));
      var job = new Publication("job", "content", platform, "account", "PROCESSING", "session", null,
          "title", "caption", "VIDEO", "video/mp4", 4, "public");
      doReturn(job, (Publication) null).when(worker).claim();
      when(publisher.advance(job)).thenThrow(new PublishFailure("UNKNOWN", "Transport failure"));
      worker.run();
      verify(jdbc).update(contains("error_message=?,next_attempt_at=TIMESTAMPADD"),
          eq(platform.equals("youtube") ? "PROCESSING" : "UNKNOWN"), eq("Transport failure"), eq(300L), eq("job"));
      verify(publisher, times(1)).advance(job);
    }
  }

  @Test void disabledWorkerDoesNotTouchDatabaseOrProviders() {
    var properties = new PublishingProperties();
    properties.setEnabled(false);
    var jdbc = mock(JdbcTemplate.class);
    var publisher = mock(SocialPublisher.class);
    new PublishingWorker(jdbc, mock(TransactionTemplate.class), properties, publisher, mock(PublishingTokens.class)).run();
    verifyNoInteractions(jdbc, publisher);
  }
}
