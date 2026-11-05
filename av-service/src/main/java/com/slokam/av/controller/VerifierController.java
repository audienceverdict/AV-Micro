package com.slokam.av.controller;

import com.slokam.av.entity.Show;
import com.slokam.av.service.RoleAccessService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/verifier")
public class VerifierController {
    private final RoleAccessService access;

    public VerifierController(RoleAccessService access) { this.access = access; }

    @io.swagger.v3.oas.annotations.Operation(summary = "List the logged-in verifier's assigned shows",
            description = "Returns current assigned shows, including cancelled shows, ordered by date, start time and ID. Returns an empty array when no shows are assigned.")
    @GetMapping("/shows")
    public List<Show> shows(Principal principal) {
        return access.assignedShows(principal.getName());
    }
}
