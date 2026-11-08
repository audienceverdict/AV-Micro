package com.slokam.social.assignments;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value="/api", produces="application/json")
@Tag(name = "Audience video assignments", description = "Assign application videos (VID-UUID) to audience users. Audience users can assign only to themselves and read only their own assignments. Admin and SOCIAL_MEDIA_MANAGER can manage and read all assignments.")
public class VideoAssignmentController {
  private final VideoAssignmentService service;
  public VideoAssignmentController(VideoAssignmentService service) { this.service = service; }
  public record BulkRequest(
      @Schema(description = "Application video IDs; every selected video is assigned to every selected user.", example = "[\"VID-00000000-0000-0000-0000-000000000001\"]")
      @NotEmpty @Size(max=50) List<@NotBlank @Size(max=64) String> videoIds,
      @Schema(description = "Enabled Identity user IDs.")
      @NotEmpty @Size(max=100) List<@NotBlank @Size(max=36) String> userIds) {}
  public record SelfRequest(
      @Schema(description = "Application video IDs. Recipient is derived exclusively from the JWT subject.")
      @NotEmpty @Size(max=50) List<@NotBlank @Size(max=64) String> videoIds) {}

  @PostMapping("/video-assignments")
  @Operation(summary = "Assign videos to audience users (Admin or Social Media Manager)", description = "Assigns each video to each user, up to 50 videos and 100 users. Validates all videos and enabled recipients before writing; invalid requests roll back. Existing pairs are unchanged and counted as alreadyAssigned. Returns 200 for both new assignments and retries. Identity outages return 503.")
  public VideoAssignmentService.Result assign(@Valid @RequestBody BulkRequest body, Authentication actor) {
    return service.assign(body.videoIds(), body.userIds(), actor, false);
  }
  @PostMapping("/me/video-assignments")
  @Operation(summary = "Assign videos to myself", description = "Any authenticated audience user can self-assign. Recipient is the JWT subject; this endpoint cannot assign videos to other users. Existing assignments retain their original assigner and time.")
  public VideoAssignmentService.Result selfAssign(@Valid @RequestBody SelfRequest body, Authentication actor) {
    return service.assign(body.videoIds(), List.of(), actor, true);
  }
  @DeleteMapping("/video-assignments")
  @Operation(summary = "Unassign videos from users (Admin only)", description = "Removes each selected video/user pair, up to 50 videos and 100 users. Duplicate IDs are counted once. Missing assignments are no-ops. Returns removed and alreadyUnassigned counts. Does not require enabled recipients or Identity availability.")
  public VideoAssignmentService.UnassignResult unassign(@Valid @RequestBody BulkRequest body, Authentication actor) {
    return service.unassign(body.videoIds(), body.userIds(), actor, false);
  }
  @DeleteMapping("/me/video-assignments")
  @Operation(summary = "Unassign videos from myself", description = "Removes only the JWT subject's assignments, including assignments created by an admin or manager. Supports up to 50 videos. Missing assignments are no-ops. Recipient IDs supplied in the body cannot change the JWT-derived recipient.")
  public VideoAssignmentService.UnassignResult selfUnassign(@Valid @RequestBody SelfRequest body, Authentication actor) {
    return service.unassign(body.videoIds(), List.of(), actor, true);
  }
  @GetMapping("/me/video-assignments")
  @Operation(summary = "Read my assigned videos", description = "Returns only the JWT subject's videos, including assignments by self, Admin and Social Media Manager. Includes linked uploads, recipient ID, assigning user ID, source and UTC assignment timestamp. Zero-based pagination, newest first.")
  public VideoAssignmentService.Page mine(Authentication actor,
      @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
    return service.list(actor, actor.getName(), null, null, null, page, size);
  }
  @GetMapping("/video-assignments")
  @Operation(summary = "Read all video assignments (Admin or Social Media Manager)", description = "Paginated assignment rows with optional recipient, video, assigning user and source filters. Includes video details and linked uploads. Audience users receive 403.")
  public VideoAssignmentService.Page all(Authentication actor,
      @RequestParam(required=false) String userId, @RequestParam(required=false) String videoId,
      @RequestParam(required=false) String assignedBy,
      @RequestParam(required=false) @Pattern(regexp="SELF|ADMIN|SOCIAL_MEDIA_MANAGER") String source,
      @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
    VideoAssignmentService.requireManager(actor);
    return service.list(actor, userId, videoId, assignedBy, source, page, size);
  }
  @GetMapping("/users/{userId}/video-assignments")
  @Operation(summary = "Read a user's assigned videos", description = "Admin and Social Media Manager can read any user. Other authenticated users may read only their own user ID; another user's ID returns 403. An empty assignment list returns 200.")
  public VideoAssignmentService.Page user(Authentication actor, @PathVariable String userId,
      @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
    return service.list(actor, userId, null, null, null, page, size);
  }
  @GetMapping("/videos/{videoId}/assigned-users")
  @Operation(summary = "Read users assigned to a video (Admin or Social Media Manager)", description = "Paginated assignment rows identifying recipients by userId, with assigner, source and timestamp. Empty or unknown video IDs return an empty page. Audience users receive 403.")
  public VideoAssignmentService.Page recipients(Authentication actor, @PathVariable String videoId,
      @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
    VideoAssignmentService.requireManager(actor);
    return service.list(actor, null, videoId, null, null, page, size);
  }
}
