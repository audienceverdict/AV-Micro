package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.slokam.social.api.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class VideoServiceTest {
  @Test
  void platformFilterIsAppliedBeforePagination() {
    var jdbc = mock(JdbcTemplate.class);
    var service = new VideoService(jdbc);
    service.unassigned(50, 20, " youtube ");
    verify(jdbc).queryForList(
        argThat((String sql) -> sql.contains("c.thumbnail_url")
            && sql.contains("AND c.platform=? ORDER BY c.id LIMIT ? OFFSET ?")),
        eq("youtube"), eq(20), eq(50));
  }

  @Test
  void invalidPlatformIsRejected() {
    var jdbc = mock(JdbcTemplate.class);
    var error = assertThrows(ResponseStatusException.class,
        () -> new VideoService(jdbc).unassigned(0, 50, "other"));
    assertEquals(400, error.getStatusCode().value());
    verifyNoInteractions(jdbc);
  }
}
