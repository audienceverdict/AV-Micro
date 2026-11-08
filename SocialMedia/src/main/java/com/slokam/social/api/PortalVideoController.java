package com.slokam.social.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portal/videos")
public class PortalVideoController {
  private final PortalVideoService service;

  public PortalVideoController(PortalVideoService service) { this.service = service; }

  @Operation(summary = "List public application videos",
      description = "Public catalog of all application videos with the same master fields and ordering as the operator video list. Includes linked uploads, assets and lifetime metrics. No login, approval, account status, publication date or assignment restriction. Optional platform, topic and category filters match linked uploads. Page numbers start at zero; size is 1-200.")
  @GetMapping
  public PortalVideoService.Page videos(
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
      @RequestParam(required = false) String platform,
      @RequestParam(required = false) String topic,
      @RequestParam(required = false) String categoryName) {
    return service.list(page, size, platform, topic, categoryName);
  }
}
