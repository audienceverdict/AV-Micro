package com.slokam.av.security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
@Service
public class JwtService {
 private final JwtDecoder decoder;
 public JwtService(@Value("${jwt.jwk-set-uri}") String uri) {
  var nimbus=NimbusJwtDecoder.withJwkSetUri(uri).jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build();
  nimbus.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
    new JwtTimestampValidator(java.time.Duration.ZERO),new JwtIssuerValidator("audience-verdict"),
    new JwtClaimValidator<java.util.List<String>>("aud", aud->aud!=null&&aud.contains("av-api"))));
  decoder=nimbus;
 }
 public Jwt validate(String token) { var jwt=decoder.decode(token); if(jwt.hasClaim("purpose"))throw new BadJwtException("Access token required"); return jwt; }
}
