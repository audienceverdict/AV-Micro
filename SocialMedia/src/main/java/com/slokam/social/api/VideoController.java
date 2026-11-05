package com.slokam.social.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Videos and assignments")
@RestController
@RequestMapping("/api")
@Validated
public class VideoController {
  private final VideoService service;
  private final com.slokam.social.comments.CommentStore comments;

  public VideoController(VideoService s, com.slokam.social.comments.CommentStore comments) {
    service = s;
    this.comments = comments;
  }

  public record CreateVideo(
      @NotBlank @Size(max = 255) String title, @Size(max = 10000) String description) {}

  @Operation(summary = "Create an application video", description = "Creates a local grouping with an immutable VID-UUID ID. This does not upload or publish media.")
  @PostMapping("/videos")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@Valid @RequestBody CreateVideo body) {
    return service.create(body.title(), body.description());
  }

  @Operation(summary = "List application videos", description = "Offset pagination ordered by creation time and ID.")
  @GetMapping("/videos")
  public List<Map<String, Object>> list(
      @Parameter(description = "Number of rows to skip.") @RequestParam(defaultValue = "0") @Min(0) int offset,
      @Parameter(description = "Maximum rows to return (1-200).") @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
    return service.list(offset, limit);
  }

  @Operation(summary = "Get an application video", description = "Returns the stored video master.")
  @GetMapping("/videos/{videoId}")
  public Map<String, Object> get(@Parameter(description = "Local application video ID (VID-UUID).") @PathVariable String videoId) {
    return service.video(videoId);
  }

  @Operation(summary = "List unassigned video uploads", description = "Returns local VIDEO, SHORT_VIDEO, REEL and LIVE uploads available for assignment, ordered by local ID across all platforms. A page may contain only one platform; use platform to filter or increase offset to continue. IDs are local content IDs, not provider IDs.")
  @GetMapping("/uploads/unassigned")
  public List<Map<String, Object>> unassigned(
      @Parameter(description = "Number of rows to skip.") @RequestParam(defaultValue = "0") @Min(0) int offset,
      @Parameter(description = "Maximum rows to return (1-200).") @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @Parameter(description = "Optional platform: INSTAGRAM, YOUTUBE or FACEBOOK (case insensitive).") @RequestParam(required = false) String platform) {
    return service.unassigned(offset, limit, platform);
  }

  @Operation(summary = "List linked uploads", description = "Returns provider IDs, links, stored engagement metadata and retrieval times for the current assignments.")
  @GetMapping("/videos/{videoId}/uploads")
  public List<Map<String, Object>> uploads(@Parameter(description = "Local application video ID (VID-UUID).") @PathVariable String videoId) {
    return service.uploads(videoId);
  }

  @Operation(summary = "Assign a video upload", description = "Idempotent for the same video. Returns 409 if already assigned elsewhere; use the explicit reassignment endpoint.")
  @PutMapping("/videos/{videoId}/uploads/{contentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assign(@Parameter(description = "Local application video ID (VID-UUID).") @PathVariable String videoId, @Parameter(description = "Local social_content.id, not the provider content ID.") @PathVariable @Positive long contentId) {
    service.assign(videoId, contentId, false);
  }

  @Operation(summary = "Move a video upload", description = "Explicitly moves or assigns the upload to this video and records the change in the audit.")
  @PutMapping("/videos/{videoId}/uploads/{contentId}/reassign")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reassign(@Parameter(description = "Local application video ID (VID-UUID).") @PathVariable String videoId, @Parameter(description = "Local social_content.id, not the provider content ID.") @PathVariable @Positive long contentId) {
    service.assign(videoId, contentId, true);
  }

  @Operation(summary = "Unlink a video upload", description = "Removes the assignment without deleting content or comments. Already unlinked uploads are a no-op.")
  @DeleteMapping("/videos/{videoId}/uploads/{contentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unlink(@Parameter(description = "Local application video ID (VID-UUID).") @PathVariable String videoId, @Parameter(description = "Local social_content.id, not the provider content ID.") @PathVariable @Positive long contentId) {
    service.unlink(videoId, contentId);
  }

  @Operation(summary = "List consolidated comments", description = "Returns stored comments and replies across linked uploads in ascending local ID order. Continue with the last id as afterId; restart at zero for edits or assignment changes.")
  @GetMapping("/videos/{videoId}/comments")
  public List<Map<String, Object>> comments(
      @Parameter(description = "Local application video ID (VID-UUID).") @PathVariable String videoId,
      @Parameter(description = "Exclusive local ID cursor. Use 0 for the first page; see operation description for the response cursor field.") @RequestParam(defaultValue = "0") @Min(0) long afterId,
      @Parameter(description = "Maximum rows to return (1-200).") @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
    return service.comments(videoId, afterId, limit);
  }

  @Operation(summary = "Reset comment synchronization", description = "Restarts existing comment tasks from the first page for idempotent replay. Returns 409 while the account lease is occupied. Does not perform a synchronous provider fetch.")
  @PostMapping("/uploads/{contentId}/comment-sync/reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reset(@Parameter(description = "Local social_content.id, not the provider content ID.") @PathVariable @Positive long contentId) {
    comments.reset(contentId);
  }

  @Operation(summary = "Get comment synchronization progress", description = "Returns per-upload and thread task status, pagination progress, errors and due times.")
  @GetMapping("/videos/{videoId}/comment-sync")
  public List<Map<String, Object>> status(@Parameter(description = "Local application video ID (VID-UUID).") @PathVariable String videoId) {
    return service.status(videoId);
  }

  @Operation(summary = "List assignment audit records", description = "Offset pagination in descending audit ID order. Includes assignments to and from this video.")
  @GetMapping("/videos/{videoId}/assignment-audit")
  public List<Map<String, Object>> audit(
      @Parameter(description = "Local application video ID (VID-UUID).") @PathVariable String videoId,
      @Parameter(description = "Number of rows to skip.") @RequestParam(defaultValue = "0") @Min(0) int offset,
      @Parameter(description = "Maximum rows to return (1-200).") @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
    return service.audit(videoId, offset, limit);
  }
}
