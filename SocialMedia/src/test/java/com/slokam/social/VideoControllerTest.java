package com.slokam.social;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.slokam.social.api.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class VideoControllerTest {
  final VideoService service = mock(VideoService.class);
  final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(
              new VideoController(service, mock(com.slokam.social.comments.CommentStore.class)))
          .build();

  @Test
  void createValidatesInput() throws Exception {
    mvc.perform(post("/api/videos").contentType("application/json").content("{\"title\":\" \"}"))
        .andExpect(status().isBadRequest());
    when(service.create("Demo", null)).thenReturn(Map.of("id", "VID-demo"));
    mvc.perform(post("/api/videos").contentType("application/json").content("{\"title\":\"Demo\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("VID-demo"));
  }

  @Test
  void assignmentConflictAndExplicitReassignment() throws Exception {
    doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Already assigned"))
        .when(service)
        .assign("VID-demo", 10L, false);
    mvc.perform(put("/api/videos/VID-demo/uploads/10")).andExpect(status().isConflict());
    mvc.perform(put("/api/videos/VID-demo/uploads/10/reassign")).andExpect(status().isNoContent());
    verify(service).assign("VID-demo", 10L, true);
  }

  @Test
  void unassignedSupportsPlatformAndPreservesCombinedPagination() throws Exception {
    mvc.perform(get("/api/uploads/unassigned?offset=50&limit=50"))
        .andExpect(status().isOk());
    verify(service).unassigned(50, 50, null);
    mvc.perform(get("/api/uploads/unassigned?platform=YOUTUBE&offset=0&limit=20"))
        .andExpect(status().isOk());
    verify(service).unassigned(0, 20, "YOUTUBE");
    mvc.perform(get("/api/uploads/unassigned?platform=FACEBOOK"))
        .andExpect(status().isOk());
    verify(service).unassigned(0, 50, "FACEBOOK");
  }

  @Test
  void consolidatedCommentsUseStableCursor() throws Exception {
    mvc.perform(get("/api/videos/VID-demo/comments?afterId=42&limit=20"))
        .andExpect(status().isOk());
    verify(service).comments("VID-demo", 42, 20);
  }
}
