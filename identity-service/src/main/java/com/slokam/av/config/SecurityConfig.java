package com.slokam.av.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.av.exception.ApiErrors;
import com.slokam.av.filter.JwtAuthenticationFilter;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.*;

@Configuration
public class SecurityConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);
    @Bean
    SecurityFilterChain security(
            HttpSecurity http, JwtAuthenticationFilter jwt, ObjectMapper mapper) throws Exception {
        return http.csrf(c -> c.disable())
                .cors(c -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers("/.well-known/jwks.json", "/actuator/health", "/actuator/health/**").permitAll()
                                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/auth/email-otp/request",
                                                "/api/v1/auth/email-otp/verify",
                                                "/api/v1/auth/email-otp/register",
                                                "/api/v1/auth/otp/request",
                                                "/api/v1/auth/otp/verify",
                                                "/api/v1/auth/otp/register",
                                                "/api/v1/auth/otp/registration/request",
                                                "/api/v1/auth/otp/registration/verify",
                                                "/api/v1/auth/otp/registration/email/request",
                                                "/api/v1/auth/otp/registration/email/verify")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings/admin/ticket-scan")
                                        .hasAnyRole("ADMIN", "VERIFIER")
                                        .requestMatchers(
                                                "/api/v1/admin/**",
                                                "/api/v1/bookings/admin/**",
                                                "/api/v1/reviews/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/v1/notifications/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/v1/waiting-list/admin")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/v1/notifications")
                                        .authenticated()
                                        .requestMatchers(
                                                "/api/v1/bookings/**", "/api/v1/waiting-list/**")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.POST, "/api/v1/people/**").hasRole("ADMIN")
                                        .requestMatchers(HttpMethod.PUT, "/api/v1/people/**").hasRole("ADMIN")
                                        .requestMatchers(HttpMethod.DELETE, "/api/v1/people/**").hasRole("ADMIN")
                                        .requestMatchers(HttpMethod.PATCH, "/api/v1/movies/**", "/api/v1/people/**").hasRole("ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/api/v1/theatres/screens/*/layout-compatibility").hasRole("ADMIN")
                                        .requestMatchers("/api/v1/verifier/**").hasRole("VERIFIER")
                                        .requestMatchers(HttpMethod.GET, "/api/v1/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/movies/**",
                                                "/api/v1/theatres/**",
                                                "/api/v1/shows/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.PUT,
                                                "/api/v1/movies/**",
                                                "/api/v1/theatres/**",
                                                "/api/v1/shows/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.DELETE,
                                                "/api/v1/movies/**",
                                                "/api/v1/theatres/**",
                                                "/api/v1/shows/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (r, s, x) -> {
                                                    log.warn("Request rejected: authentication required");
                                                    s.setStatus(401);
                                                    s.setContentType("application/json");
                                                    mapper.writeValue(
                                                            s.getOutputStream(),
                                                            ApiErrors.of(
                                                                    401,
                                                                    "UNAUTHORIZED",
                                                                    "Authentication required",
                                                                    r.getRequestURI()));
                                                })
                                        .accessDeniedHandler(
                                                (r, s, x) -> {
                                                    log.warn("Request rejected: access denied");
                                                    s.setStatus(403);
                                                    s.setContentType("application/json");
                                                    mapper.writeValue(
                                                            s.getOutputStream(),
                                                            ApiErrors.of(
                                                                    403,
                                                                    "FORBIDDEN",
                                                                    "Access denied",
                                                                    r.getRequestURI()));
                                                }))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> registration(JwtAuthenticationFilter filter) {
        var bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins}") String origins) {
        var c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", c);
        return source;
    }
}
