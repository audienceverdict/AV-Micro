package com.slokam.social.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portal/videos")
@Validated
public class PortalVideoController {
  private final PortalVideoService service;

  public PortalVideoController(PortalVideoService service) { this.service = service; }

  @Operation(summary = "List videos for authenticated portal users",
      description = "Newest available video posts from enabled accounts. Optional case-insensitive platform filter. Page numbers start at zero.")
  @GetMapping
  public PortalVideoService.Page videos(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) String platform) {
    return service.list(page, size, platform);
  }
}
