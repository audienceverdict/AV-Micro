package com.slokam.av.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.av.exception.ApiErrors;
import com.slokam.av.security.CustomUserDetailsService;
import com.slokam.av.security.JwtService;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwt;
    private final CustomUserDetailsService users;
    private final ObjectMapper mapper;

    public JwtAuthenticationFilter(
            JwtService jwt, CustomUserDetailsService users, ObjectMapper mapper) {
        this.jwt = jwt;
        this.users = users;
        this.mapper = mapper;
    }

    protected boolean shouldNotFilter(HttpServletRequest r) {
        return "OPTIONS".equals(r.getMethod())
                || ("POST".equals(r.getMethod())
                        && (r.getRequestURI().equals("/api/v1/auth/email-otp/request")
                                || r.getRequestURI().equals("/api/v1/auth/email-otp/verify")
                                || r.getRequestURI().equals("/api/v1/auth/email-otp/register")
                                || r.getRequestURI().equals("/api/v1/auth/otp/request")
                                || r.getRequestURI().equals("/api/v1/auth/otp/verify")
                                || r.getRequestURI().equals("/api/v1/auth/otp/register")
                                || r.getRequestURI().equals("/api/v1/auth/otp/registration/request")
                                || r.getRequestURI().equals("/api/v1/auth/otp/registration/verify")
                                || r.getRequestURI().equals("/api/v1/auth/otp/registration/email/request")
                                || r.getRequestURI().equals("/api/v1/auth/otp/registration/email/verify")));
    }

    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
                var token = jwt.validate(header.substring(7));
                var user = users.loadUserByUsername(token.getSubject());
                if (!user.isEnabled()) throw new IllegalArgumentException();
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        user, null, user.getAuthorities()));
                log.debug("JWT authentication succeeded");
            } catch (com.slokam.av.exception.custom.ApiException e) {
                SecurityContextHolder.clearContext();
                response.setStatus(e.status); response.setContentType("application/json");
                mapper.writeValue(response.getOutputStream(),ApiErrors.of(e.status,e.code,e.getMessage(),request.getRequestURI())); return;
            } catch (org.springframework.security.oauth2.jwt.JwtException
                    | org.springframework.security.core.AuthenticationException
                    | IllegalArgumentException e) {
                log.warn("Authentication rejected failureType={}", e.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                response.setContentType("application/json");
                mapper.writeValue(
                        response.getOutputStream(),
                        ApiErrors.of(
                                401,
                                "UNAUTHORIZED",
                                "Authentication required",
                                request.getRequestURI()));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
