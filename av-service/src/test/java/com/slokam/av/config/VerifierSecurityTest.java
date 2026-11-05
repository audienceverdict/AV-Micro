package com.slokam.av.config;

import com.slokam.av.controller.BookingController;
import com.slokam.av.controller.VerifierController;
import com.slokam.av.controller.TicketScanHistoryController;
import com.slokam.av.repository.TicketScanHistoryRepository;
import com.slokam.av.filter.JwtAuthenticationFilter;
import com.slokam.av.security.*;
import com.slokam.av.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;

@WebMvcTest(controllers = {BookingController.class, VerifierController.class, TicketScanHistoryController.class}, properties = "app.cors-origins=http://localhost:3000")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class VerifierSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean TicketScanHistoryRepository history;
    @MockitoBean BookingService bookings;
    @MockitoBean RoleAccessService access;
    @MockitoBean JwtService jwt;
    @MockitoBean CustomUserDetailsService users;

    @Test void scanHistoryIsAdminOnlyAndValidatesPagination() throws Exception {
        String path="/api/v1/admin/ticket-scans";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("staff").roles("VERIFIER"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("customer").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("admin").roles("ADMIN")).param("size","101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path).with(user("admin").roles("ADMIN")).param("page","-1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(history);
        when(history.history(eq("staff"),eq("customer"),eq("show"),eq("theatre"),any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get(path).with(user("admin").roles("ADMIN"))
                .param("verifierId","staff").param("customerId","customer")
                .param("showId","show").param("theatreId","theatre"))
                .andExpect(status().isOk());
        verify(history).history("staff","customer","show","theatre",org.springframework.data.domain.PageRequest.of(0,20));
    }

    @Test void assignedShowsRequireVerifierAndUseAuthenticatedIdentity() throws Exception {
        String path = "/api/v1/verifier/shows";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String role : new String[]{"USER", "ADMIN", "THEATER_MANAGER", "MOVIE_MAKER"}) {
            mvc.perform(get(path).with(user("other").roles(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(access);
        when(access.assignedShows("staff")).thenReturn(java.util.List.of());
        mvc.perform(get(path).with(user("staff").roles("USER", "VERIFIER")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(access).assignedShows("staff");
    }

    @Test void scanRequiresAdminOrVerifier() throws Exception {
        String path = "/api/v1/bookings/admin/ticket-scan";
        String body = "{\"qrPayload\":\"AVT1:01234567890123456789012345678901\",\"showId\":\"show\"}";
        mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        for (String role : new String[]{"USER", "THEATER_MANAGER", "MOVIE_MAKER"}) {
            mvc.perform(post(path).with(user("staff").roles(role)).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(bookings, access);
        for (String role : new String[]{"ADMIN", "VERIFIER"}) {
            mvc.perform(post(path).with(user("staff").roles(role)).contentType("application/json").content(body))
                    .andExpect(status().isOk());
        }
        verify(access, times(2)).requireTicketScanAccess("staff", "show");
    }

    @Test void legacyTokenWithoutRolesClaimUsesCurrentDatabaseAuthorities() throws Exception {
        var token=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("legacy-token")
                .header("alg", "HS256").subject("staff").claim("role", "USER").build();
        when(jwt.validate("legacy-token")).thenReturn(token);
        when(users.loadUserByUsername("staff")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("staff").password("")
                        .roles("USER", "VERIFIER").build());
        String path="/api/v1/bookings/admin/ticket-scan";
        String body="{\"qrPayload\":\"AVT1:01234567890123456789012345678901\",\"showId\":\"show\"}";
        mvc.perform(post(path).header("Authorization","Bearer legacy-token")
                .contentType("application/json").content(body)).andExpect(status().isOk());
        when(users.loadUserByUsername("staff")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("staff").password("").roles("USER").build());
        mvc.perform(post(path).header("Authorization","Bearer legacy-token")
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        verify(bookings, times(1)).verifyTicket(anyString(), eq("show"), eq("staff"));
    }

    @Test void deniedShowDoesNotReachTicketVerification() throws Exception {
        doThrow(new com.slokam.av.exception.custom.ApiException(403, "FORBIDDEN", "Show not assigned"))
                .when(access).requireTicketScanAccess("staff", "show");
        mvc.perform(post("/api/v1/bookings/admin/ticket-scan").with(user("staff").roles("VERIFIER"))
                        .contentType("application/json")
                        .content("{\"qrPayload\":\"AVT1:01234567890123456789012345678901\",\"showId\":\"show\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bookings);
    }

    @Test void verifierCannotUseOtherAdminOperationsButAdminStillCan() throws Exception {
        String path = "/api/v1/bookings/admin/booking/attendance?attended=true";
        mvc.perform(post(path).with(user("staff").roles("USER", "VERIFIER"))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        verify(bookings).markAttended("booking", true);
    }
}
