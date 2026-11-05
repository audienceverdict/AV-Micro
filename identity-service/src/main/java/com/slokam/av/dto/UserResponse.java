package com.slokam.av.dto;

import com.slokam.av.entity.Role;
import com.slokam.av.entity.User;

import java.time.Instant;

public record UserResponse(
        String id,
        String name,
        String mobile,
        String email,
        String alternativeMobile,
        Role role,
        boolean enabled,
        Instant createdAt,
        java.util.Set<Role> roles) {
    public UserResponse(String id, String name, String mobile, String email,
            String alternativeMobile, Role role, boolean enabled, Instant createdAt) {
        this(id, name, mobile, email, alternativeMobile, role, enabled, createdAt, java.util.Set.of(role));
    }
    public static UserResponse of(User u) {
        return new UserResponse(
                u.id,
                u.name,
                u.mobile,
                u.email,
                u.alternativeMobile,
                u.role,
                u.enabled,
                u.createdAt,
                u.effectiveRoles());
    }
}
