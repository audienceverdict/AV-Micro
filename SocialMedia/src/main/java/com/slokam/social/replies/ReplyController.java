package com.slokam.social.replies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
@Tag(name = "Comment inbox and replies")
@SecurityRequirement(name = "identityBearer")
@RestController @RequestMapping("/api") @Validated
public class ReplyController {
  private final ReplyService service; private final ReplyAccess access;
  public ReplyController(ReplyService service, ReplyAccess access) { this.service=service; this.access=access; }
  private ReplyAccess.Actor readActor(HttpServletRequest request) {
    String name=access.reader();
    var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    if(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
      return access.authenticate(request);
    return service.reader(name);
  }
  @ModelAttribute
  public void noCache(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }
  public record CreateReply(@NotBlank @Size(max=10000) String text) {}
  @Operation(summary = "List the operator comment inbox", description = "Identity access JWT required; users and administrators can read stored comments across all accounts. Filters are optional. Continue with the last local id, and restart at zero for updates. needsReply selects unresolved comments without a SENT reply targeting them, excluding recognized own comments; replies sent outside this application may not be recognized.")
  @GetMapping("/comments")
  public List<Map<String,Object>> inbox(HttpServletRequest request,
      @RequestParam(required=false) String platform,
      @Parameter(description = "Local social_accounts.id, not the provider account ID.") @RequestParam(required=false) Long accountId,
      @Parameter(description = "Local application video ID (VID-UUID).") @RequestParam(required=false) String videoId,@RequestParam(defaultValue="false") boolean needsReply,
      @Parameter(description = "Exclusive local ID cursor. Use 0 for the first page; see operation description for the response cursor field.") @RequestParam(defaultValue="0") @Min(0) long afterId,
      @Parameter(description = "Maximum rows to return (1-200).") @RequestParam(defaultValue="50") @Min(1) @Max(200) int limit) {
    return service.inbox(readActor(request),platform,accountId,videoId,needsReply,afterId,limit);
  }
  @Operation(summary = "Get a stored comment conversation", description = "Returns the selected comment, locally stored thread and outbound reply history for an authorized account.")
  @GetMapping("/comments/{id}/thread")
  public Map<String,Object> thread(@PathVariable long id,HttpServletRequest request) { return service.thread(id,readActor(request)); }
  @Operation(summary = "Queue an outbound reply", description = "Returns a durable job, not a delivery guarantee. Idempotency-Key is scoped to the operator: the same target/text/key returns the same job, while changed target or text returns 409. Replies to child comments target the stored root thread. Provider text limits can be stricter than 10000 characters.")
  @PostMapping("/comments/{id}/replies") @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String,Object> reply(@PathVariable long id,@Parameter(description = "Required request deduplication key. Publishing accepts 1-128 letters, digits, dots, colons, underscores or hyphens; reply keys are scoped to the operator.", example = "campaign-2026-001") @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreateReply body,HttpServletRequest request) { return service.enqueue(id,key,body.text(),access.authenticate(request)); }
  @Operation(summary = "Queue a new top-level comment", description = "Administrator JWT required; administrators have access to all accounts. Uses the same idempotency and delivery states as replies.")
  @PostMapping("/content/{contentId}/comments") @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String,Object> create(@PathVariable long contentId, @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreateReply body, HttpServletRequest request) {
    return service.enqueueComment(contentId,key,body.text(),access.authenticate(request));
  }
  @Operation(summary = "Queue a top-level comment on an upload", description = "Administrator JWT required; administrators have access to all accounts. uploadId is the local social_content.id. Idempotency-Key is optional; provide it to safely repeat a request. Returns the existing asynchronous comment job response.")
  @PostMapping("/uploads/{uploadId}/comments") @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String,Object> createForUpload(@PathVariable @Min(1) long uploadId,
      @RequestHeader(value="Idempotency-Key",required=false) String key,
      @Valid @RequestBody CreateReply body, HttpServletRequest request) {
    return service.enqueueComment(uploadId,key==null ? UUID.randomUUID().toString() : key,
        body.text(),access.authenticate(request));
  }
  @Operation(summary = "Get outbound reply status", description = "Returns delivery and audit fields. States: QUEUED, SENDING, SENT, FAILED, UNKNOWN. UNKNOWN indicates ambiguous delivery and cannot be retried.")
  @GetMapping("/comment-replies/{id}")
  public Map<String,Object> get(@PathVariable String id,HttpServletRequest request) { return service.get(id,access.authenticate(request)); }
  @Operation(summary = "Retry a definitely failed reply", description = "Manually queues a FAILED job after its provider cooldown. UNKNOWN replies cannot be retried. Fix permission or credential problems before retrying.")
  @PostMapping("/comment-replies/{id}/retry") @ResponseStatus(HttpStatus.ACCEPTED)
  public void retry(@PathVariable String id,HttpServletRequest request) { service.retry(id,access.authenticate(request)); }
  public record Resolution(boolean resolved) {}
  public record Confirmation(@NotBlank String externalReplyId) {}
  @Operation(summary = "Resolve or reopen a comment", description = "Sets resolution for this selected comment only. Resolution is independent of delivery; false reopens it.")
  @PutMapping("/comments/{id}/resolution") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resolve(@PathVariable long id,@RequestBody Resolution body,HttpServletRequest request) {
    service.resolve(id,body.resolved(),access.authenticate(request));
  }
  @Operation(summary = "Confirm an uncertain delivered reply", description = "For an UNKNOWN job, the operator attests that externalReplyId exists on the provider. Records SENT without sending or remotely verifying the reply. Inspect the provider before confirming.")
  @PostMapping("/comment-replies/{id}/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirm(@PathVariable String id,@Valid @RequestBody Confirmation body,HttpServletRequest request) {
    service.confirm(id,body.externalReplyId(),access.authenticate(request));
  }
}
