package com.slokam.av.controller;

import com.slokam.av.dto.CampaignRequest;
import com.slokam.av.dto.CampaignResult;
import com.slokam.av.entity.Notification;
import com.slokam.av.repository.NotificationRepository;
import com.slokam.av.service.NotificationService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Notification")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationRepository notifications;
    private final NotificationService service;

    public NotificationController(
            NotificationRepository notifications, NotificationService service) {
        this.notifications = notifications;
        this.service = service;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List account notifications, newest first", description = "List account notifications, newest first.")
    @GetMapping
    public List<Notification> mine(Principal p) {
        return notifications.findByUserIdOrderByCreatedAtDesc(p.getName());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Send a notification campaign to the requested audience", description = "Send a notification campaign to the requested audience.")
    @PostMapping("/admin/campaigns")
    public CampaignResult send(@Valid @RequestBody CampaignRequest request) {
        return service.send(request);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Send a reminder campaign for a show", description = "Send a reminder campaign for a show.")
    @PostMapping("/admin/shows/{showId}/reminder")
    public CampaignResult reminder(@PathVariable String showId) {
        return service.sendShowReminder(showId);
    }
}
