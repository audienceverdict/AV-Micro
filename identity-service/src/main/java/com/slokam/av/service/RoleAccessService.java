package com.slokam.av.service;

import com.slokam.av.dto.AdditionalRolesRequest;
import com.slokam.av.dto.UserResponse;
import com.slokam.av.entity.*;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class RoleAccessService {
    private static final Set<Role> STAFF_ROLES = Set.of(Role.VERIFIER, Role.THEATER_MANAGER, Role.MOVIE_MAKER, Role.SOCIAL_MEDIA_MANAGER);
    private final UserRepository users;
    private final com.slokam.av.client.ShowDirectory shows;
    private final RoleAssignmentAuditRepository audits;

    public RoleAccessService(UserRepository users, com.slokam.av.client.ShowDirectory shows, RoleAssignmentAuditRepository audits) {
        this.users = users;
        this.shows = shows;
        this.audits = audits;
    }

    private User account(String id) {
        return users.findById(id).orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND", "User not found"));
    }

    private void requireAdmin(String actorId) {
        var actor = account(actorId);
        if (!actor.enabled || !actor.effectiveRoles().contains(Role.ADMIN))
            throw new ApiException(403, "FORBIDDEN", "Admin access required");
    }

    @Transactional(readOnly = true)
    public void requireTicketScanAccess(String actorId, String showId) {
        var actor = account(actorId);
        if (!actor.enabled) throw new ApiException(403, "FORBIDDEN", "Account disabled");
        if (actor.effectiveRoles().contains(Role.ADMIN)) return;
        if (!actor.effectiveRoles().contains(Role.VERIFIER) || !actor.verifierShowIds.contains(showId))
            throw new ApiException(403, "FORBIDDEN", "Verifier access to this show is required");
    }

    @Transactional(readOnly = true)
    public Set<String> verifierShows(String actorId, String userId) {
        requireAdmin(actorId);
        return Set.copyOf(account(userId).verifierShowIds);
    }

    @Transactional
    public UserResponse replace(String actorId, String userId, AdditionalRolesRequest request) {
        requireAdmin(actorId);
        if (!STAFF_ROLES.containsAll(request.roles()))
            throw new ApiException(400, "INVALID_ADDITIONAL_ROLE", "Only staff roles can be assigned here");
        if (!request.roles().contains(Role.VERIFIER) && !request.verifierShowIds().isEmpty())
            throw new ApiException(400, "VERIFIER_ROLE_REQUIRED", "Show assignments require the verifier role");
        for (String showId : request.verifierShowIds()) {
            if (!shows.existsById(showId))
                throw new ApiException(404, "SHOW_NOT_FOUND", "Assigned show not found");
        }
        var user = account(userId);
        var audit = new RoleAssignmentAudit();
        audit.actorId = actorId;
        audit.userId = userId;
        audit.previousAssignments = snapshot(user);
        user.additionalRoles.clear();
        user.additionalRoles.addAll(request.roles());
        user.verifierShowIds.clear();
        user.verifierShowIds.addAll(request.verifierShowIds());
        audit.newAssignments = snapshot(user);
        audits.save(audit);
        return UserResponse.of(users.save(user));
    }

    private String snapshot(User user) {
        return "roles=" + new TreeSet<>(user.additionalRoles) + ";shows=" + new TreeSet<>(user.verifierShowIds);
    }
}
