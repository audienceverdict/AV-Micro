package com.slokam.av.controller;

import com.slokam.av.dto.AdditionalRolesRequest;
import com.slokam.av.dto.UserResponse;
import com.slokam.av.service.RoleAccessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminRoleController {
    private final RoleAccessService access;
    public AdminRoleController(RoleAccessService access) { this.access = access; }

    @io.swagger.v3.oas.annotations.Operation(summary = "Replace additional staff roles and verifier show assignments",
            description = "Preserves the primary USER or ADMIN role. Empty arrays revoke additional access. Theater Manager and Movie Maker grant no additional permissions yet.")
    @PutMapping("/{id}/additional-roles")
    public UserResponse replace(@PathVariable String id, @Valid @RequestBody AdditionalRolesRequest request,
            Principal principal) {
        return access.replace(principal.getName(), id, request);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get verifier show assignments for an account",
            description = "Returns the show IDs assigned to the account for QR verification.")
    @GetMapping("/{id}/verifier-shows")
    public Set<String> verifierShows(@PathVariable String id, Principal principal) {
        return access.verifierShows(principal.getName(), id);
    }
}
