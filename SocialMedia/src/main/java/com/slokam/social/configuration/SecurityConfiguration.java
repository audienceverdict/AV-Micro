package com.slokam.social.configuration;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
  @Bean
  JwtDecoder socialJwtDecoder(@Value("${jwt.jwk-set-uri}") String uri) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(uri).jwsAlgorithm(SignatureAlgorithm.RS256).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator("audience-verdict"),
        new JwtClaimValidator<List<String>>("aud", a -> a != null && a.contains("av-api")),
        jwt -> jwt.hasClaim("purpose")
            ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Access token required", null))
            : OAuth2TokenValidatorResult.success()));
    return decoder;
  }

  @Bean
  SecurityFilterChain socialSecurity(HttpSecurity http) throws Exception {
    var authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a
            .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**",
                "/api/content/*/media", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                "/swagger-ui.html", "/swagger-ui/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/portal/videos").authenticated()
            .anyRequest().hasRole("ADMIN"))
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
        .build();
  }
}
