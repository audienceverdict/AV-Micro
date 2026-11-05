package com.slokam.av.service;

import com.slokam.av.dto.*;
import com.slokam.av.entity.*;
import com.slokam.av.entity.Role;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.*;
import com.slokam.av.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=RoleAccessIntegrationTest.Config.class, properties={
    "spring.datasource.url=jdbc:h2:mem:roles;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.flyway.enabled=false",
    "jwt.secret=roles-test-secret-at-least-thirty-two-bytes"})
@ActiveProfiles("roles-test")
class RoleAccessIntegrationTest {
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    @EntityScan("com.slokam.av.entity") @EnableJpaRepositories("com.slokam.av.repository")
    @Import({RoleAccessService.class, CustomUserDetailsService.class, JwtService.class})
    static class Config {}
    @Autowired UserRepository users;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.slokam.av.client.ShowDirectory shows;
    @Autowired RoleAssignmentAuditRepository audits;
    @Autowired RoleAccessService access;
    @Autowired CustomUserDetailsService details;
    @Autowired JwtService jwt;

    private User account(Role role) {
        var u = new User(); u.mobile = UUID.randomUUID().toString().substring(0,20); u.role = role;
        return users.saveAndFlush(u);
    }
    static class TestShow { String id=UUID.randomUUID().toString(); }
    private TestShow show() {
        var s=new TestShow();org.mockito.Mockito.when(shows.existsById(s.id)).thenReturn(true);return s;
    }
    @Test void legacyAccountsAndTokensRetainAccessAndClaims() {
        var audience=account(Role.USER); var admin=account(Role.ADMIN);
        assertEquals(Set.of(Role.USER), UserResponse.of(audience).roles());
        assertEquals(Role.USER, UserResponse.of(audience).role());
        assertEquals("USER", jwt.validate(jwt.generate(audience)).getClaimAsString("role"));
        access.requireTicketScanAccess(admin.id,"any-show");
        assertThrows(ApiException.class,()->access.requireTicketScanAccess(audience.id,"any-show"));
    }
    @Test void assignmentsPersistAndRevocationUpdatesAuthoritiesWithoutNewToken() {
        var admin=account(Role.ADMIN); var staff=account(Role.USER); var show=show();
        String oldToken=jwt.generate(staff);
        long before=audits.count();
        var result=access.replace(admin.id, staff.id, new AdditionalRolesRequest(Set.of(Role.VERIFIER),Set.of(show.id)));
        assertEquals(Role.USER,result.role());
        assertEquals(Set.of(Role.USER,Role.VERIFIER),result.roles());
        assertEquals(Set.of(show.id),access.verifierShows(admin.id,staff.id));
        var persisted=users.findById(staff.id).orElseThrow();
        assertEquals(Set.of("USER","VERIFIER"),new HashSet<>(jwt.validate(jwt.generate(persisted)).getClaimAsStringList("roles")));
        String subject=jwt.validate(oldToken).getSubject();
        assertTrue(details.loadUserByUsername(subject).getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_VERIFIER")));
        access.requireTicketScanAccess(staff.id,show.id);
        assertThrows(ApiException.class,()->access.requireTicketScanAccess(staff.id,"unassigned"));
        access.replace(admin.id,staff.id,new AdditionalRolesRequest(Set.of(),Set.of()));
        assertThrows(ApiException.class,()->access.requireTicketScanAccess(staff.id,show.id));
        assertFalse(details.loadUserByUsername(subject).getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_VERIFIER")));
        assertEquals(before+2,audits.count());
    }
    @Test void disabledAccountsCannotScanOrAssignRoles() {
        var admin=account(Role.ADMIN); var staff=account(Role.USER); var show=show();
        access.replace(admin.id,staff.id,new AdditionalRolesRequest(Set.of(Role.VERIFIER),Set.of(show.id)));
        var disabledStaff=users.findById(staff.id).orElseThrow();
        disabledStaff.enabled=false; users.saveAndFlush(disabledStaff);
        assertFalse(details.loadUserByUsername(staff.id).isEnabled());
        assertThrows(ApiException.class,()->access.requireTicketScanAccess(staff.id,show.id));
        admin.enabled=false; users.saveAndFlush(admin);
        assertThrows(ApiException.class,()->access.replace(admin.id,staff.id,new AdditionalRolesRequest(Set.of(),Set.of())));
    }

    @Test void roleManagementRejectsEscalationAndInvalidAssignments() {
        var admin=account(Role.ADMIN); var staff=account(Role.USER);
        assertThrows(ApiException.class,()->access.replace(staff.id,staff.id,new AdditionalRolesRequest(Set.of(Role.VERIFIER),Set.of())));
        assertThrows(ApiException.class,()->access.replace(admin.id,staff.id,new AdditionalRolesRequest(Set.of(Role.ADMIN),Set.of())));
        assertThrows(ApiException.class,()->access.replace(admin.id,staff.id,new AdditionalRolesRequest(Set.of(),Set.of("show"))));
        assertThrows(ApiException.class,()->access.replace(admin.id,staff.id,new AdditionalRolesRequest(Set.of(Role.VERIFIER),Set.of("missing"))));
        access.replace(admin.id,staff.id,new AdditionalRolesRequest(Set.of(Role.THEATER_MANAGER,Role.MOVIE_MAKER),Set.of()));
        assertThrows(ApiException.class,()->access.requireTicketScanAccess(staff.id,"show"));
    }
}
