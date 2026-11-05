package com.slokam.av.controller;

import com.slokam.av.dto.WaitingListRequest;
import com.slokam.av.entity.WaitingListEntry;
import com.slokam.av.service.WaitingListService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Waiting List")
@RestController
@RequestMapping("/api/v1/waiting-list")
public class WaitingListController {
    private final WaitingListService service;

    public WaitingListController(WaitingListService service) {
        this.service = service;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List waiting-list entries owned by the authenticated account", description = "List waiting-list entries owned by the authenticated account.")
    @GetMapping
    public List<WaitingListEntry> mine(Principal p) {
        return service.mine(p);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List all waiting-list entries", description = "List all waiting-list entries.")
    @GetMapping("/admin")
    public List<WaitingListEntry> all() {
        return service.all();
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Join a show waiting list with the requested seat count (minimum one)", description = "Join a show waiting list with the requested seat count (minimum one).")
    @PostMapping
    public WaitingListEntry join(Principal p, @Valid @RequestBody WaitingListRequest r) {
        return service.join(p, r);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Cancel an entry owned by the authenticated account", description = "Cancel an entry owned by the authenticated account.")
    @DeleteMapping("/{id}")
    public void cancel(@PathVariable String id, Principal p) {
        service.cancel(id, p);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Claim a waiting-list offer owned by the authenticated account", description = "Claim a waiting-list offer owned by the authenticated account.")
    @PostMapping("/{id}/claim")
    public WaitingListEntry claim(@PathVariable String id, Principal p) {
        return service.claim(id, p);
    }
}
