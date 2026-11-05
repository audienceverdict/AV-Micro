package com.slokam.av.controller;

import com.slokam.av.dto.AccountStatusRequest;
import com.slokam.av.dto.RoleUpdateRequest;
import com.slokam.av.dto.UserResponse;
import com.slokam.av.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Admin User")
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AuthService service;

    public AdminUserController(AuthService service) {
        this.service = service;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "List accounts with zero-based pagination", description = "List accounts with zero-based pagination.")
    @GetMapping
    public Page<UserResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Get an account by ID", description = "Get an account by ID.")
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable String id) {
        return service.current(id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Change an account role to USER or ADMIN", description = "Change an account role to USER or ADMIN.")
    @PatchMapping("/{id}/role")
    public UserResponse role(@PathVariable String id, @Valid @RequestBody RoleUpdateRequest r) {
        return service.role(id, r.role());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Enable or disable an account", description = "Enable or disable an account.")
    @PatchMapping("/{id}/status")
    public UserResponse status(
            @PathVariable String id, @Valid @RequestBody AccountStatusRequest r) {
        return service.status(id, r.enabled());
    }
}
