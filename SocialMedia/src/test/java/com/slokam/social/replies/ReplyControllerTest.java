package com.slokam.social.replies;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
class ReplyControllerTest {
  @org.junit.jupiter.api.AfterEach void cleanup() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
  @Test void authenticationAndValidationPrecedeWrites() throws Exception {
    var service=mock(ReplyService.class);var settings=new ReplyProperties();var operator=new ReplyProperties.Operator();operator.setAccountIds(List.of(1L));settings.getOperators().put("alice",operator);
    var mvc=MockMvcBuilders.standaloneSetup(new ReplyController(service,new ReplyAccess(settings))).build();
    mvc.perform(get("/api/comments")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    ReplyAccessTest.login("ROLE_ADMIN");
    String auth="Bearer identity-test-token";
    mvc.perform(post("/api/comments/1/replies").header("Authorization",auth).header("Idempotency-Key","key").contentType("application/json").content("{\"text\":\" \"}")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/comments/1/replies").header("Authorization",auth).header("Idempotency-Key","key").contentType("application/json").content("{\"text\":\"Hello\"}")).andExpect(status().isAccepted());
    verify(service).enqueue(eq(1L),eq("key"),eq("Hello"),eq(new ReplyAccess.Actor("alice",List.of(),true)));
  }
  @Test void usersReadCommentsButOnlyAdminsCanPost() throws Exception {
    var service=mock(ReplyService.class);
    var settings=new ReplyProperties();var operator=new ReplyProperties.Operator();operator.setAccountIds(List.of(1L));settings.getOperators().put("alice",operator);
    var mvc=MockMvcBuilders.standaloneSetup(new ReplyController(service,new ReplyAccess(settings))).build();
    var reader=new ReplyAccess.Actor("alice",List.of(1L,2L));when(service.reader("alice")).thenReturn(reader);
    ReplyAccessTest.login("ROLE_USER");
    mvc.perform(get("/api/comments")).andExpect(status().isOk());
    verify(service).inbox(reader,null,null,null,false,0,50);
    mvc.perform(get("/api/comments/2/thread")).andExpect(status().isOk());
    verify(service).thread(2,reader);
    mvc.perform(post("/api/content/1/comments").header("Idempotency-Key","new").contentType("application/json").content("{\"text\":\"Hello\"}"))
        .andExpect(status().isForbidden());
    verify(service,never()).enqueueComment(anyLong(),anyString(),anyString(),any());
    ReplyAccessTest.login("ROLE_ADMIN");
    mvc.perform(get("/api/comments")).andExpect(status().isOk());
    verify(service).inbox(new ReplyAccess.Actor("alice",List.of(),true),null,null,null,false,0,50);
    mvc.perform(post("/api/content/1/comments").header("Idempotency-Key","new").contentType("application/json").content("{\"text\":\"Hello\"}"))
        .andExpect(status().isAccepted());
    verify(service).enqueueComment(1,"new","Hello",new ReplyAccess.Actor("alice",List.of(),true));
  }

}
