package com.slokam.av.config;

import com.slokam.av.controller.AuthController;
import com.slokam.av.filter.JwtAuthenticationFilter;
import com.slokam.av.security.CustomUserDetailsService;
import com.slokam.av.security.JwtService;
import com.slokam.av.service.AuthService;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class, properties = "app.cors-origins=http://localhost:3000")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityConfigTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;
    @MockitoBean JwtService jwtService;
    @MockitoBean CustomUserDetailsService users;

    @Test
    void otpRequestDoesNotRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/auth/email-otp/request")
                        .contentType("application/json")
                        .content("{\"email\":\"viewer@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void errorDispatchIsNotReplacedWithUnauthorized() throws Exception {
        mvc.perform(post("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    return request;
                }))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void directErrorRequestStillRequiresAuthentication() throws Exception {
        mvc.perform(post("/error")).andExpect(status().isUnauthorized());
    }

    @Test
    void profileStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test
    void layoutCompatibilityAssessmentRequiresAdmin() throws Exception {
        String path="/api/v1/theatres/screens/screen/layout-compatibility";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("viewer").roles("USER")))
                .andExpect(status().isForbidden());
    }

}
