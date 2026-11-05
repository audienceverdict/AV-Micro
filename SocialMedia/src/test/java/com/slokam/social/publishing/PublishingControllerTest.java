package com.slokam.social.publishing;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class PublishingControllerTest {
  PublishingService service = mock(PublishingService.class);
  MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublishingController(service, mock(PublishingMediaStore.class))).build();
  MockMultipartFile metadata(String json) { return new MockMultipartFile("metadata", "metadata.json", "application/json", json.getBytes()); }

  @Test void createRequiresKeyAndValidMetadata() throws Exception {
    mvc.perform(multipart("/api/content").file(metadata("{\"title\":\"Demo\",\"caption\":\"Caption\",\"type\":\"VIDEO\"}")))
        .andExpect(status().isBadRequest());
    mvc.perform(multipart("/api/content").file(metadata("{\"title\":\" \",\"caption\":\"Caption\",\"type\":\"VIDEO\"}")).header("Idempotency-Key", "one"))
        .andExpect(status().isBadRequest());
    mvc.perform(multipart("/api/content").file(metadata("{\"title\":\"Demo\",\"caption\":\"Caption\",\"type\":\"VIDEO\",\"youtubePrivacy\":\"invalid\"}")).header("Idempotency-Key", "one"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test void createPassesFileAndDefaultsToAutomaticPublishing() throws Exception {
    var file = new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[]{1});
    when(service.create(eq("one"), any(), any())).thenReturn(Map.of("id", "content1"));
    mvc.perform(multipart("/api/content")
        .file(metadata("{\"title\":\"Demo\",\"caption\":\"Caption\",\"type\":\"VIDEO\"}")).file(file).header("Idempotency-Key", "one"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("content1"));
    verify(service).create(eq("one"), argThat(request -> !request.draft() && request.type() == PublishingService.ContentType.VIDEO), any());
  }

  @Test void retryCannotResendUnknownResults() throws Exception {
    doThrow(new ResponseStatusException(HttpStatus.CONFLICT)).when(service).retry("unknown");
    mvc.perform(post("/api/publications/unknown/retry")).andExpect(status().isConflict());
    mvc.perform(post("/api/publications/failed/retry")).andExpect(status().isAccepted());
  }
}
