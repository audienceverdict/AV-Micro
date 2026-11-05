package com.slokam.social.configuration;

import static org.junit.jupiter.api.Assertions.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class IdentityJwtValidationTest {
  @Test void validatesSignatureIssuerAudienceExpirationAndTokenPurpose() throws Exception {
    var key = new RSAKeyGenerator(2048).keyID("identity-test").generate();
    var server = new WireMockServer(0);
    server.start();
    try {
      server.stubFor(get(urlEqualTo("/jwks")).willReturn(okJson(new JWKSet(key.toPublicJWK()).toString())));
      var decoder = new SecurityConfiguration().socialJwtDecoder(server.baseUrl() + "/jwks");
      assertEquals("user-id", decoder.decode(token(key,"audience-verdict","av-api",60,false)).getSubject());
      assertThrows(JwtException.class, () -> decoder.decode(token(key,"wrong","av-api",60,false)));
      assertThrows(JwtException.class, () -> decoder.decode(token(key,"audience-verdict","wrong",60,false)));
      assertThrows(JwtException.class, () -> decoder.decode(token(key,"audience-verdict","av-api",-60,false)));
      assertThrows(JwtException.class, () -> decoder.decode(token(key,"audience-verdict","av-api",60,true)));
      var attacker = new RSAKeyGenerator(2048).keyID("identity-test").generate();
      assertThrows(JwtException.class, () -> decoder.decode(token(attacker,"audience-verdict","av-api",60,false)));
    } finally { server.stop(); }
  }
  private String token(RSAKey key,String issuer,String audience,long expires,boolean registration) throws Exception {
    var claims = new JWTClaimsSet.Builder().subject("user-id").issuer(issuer).audience(audience)
        .expirationTime(Date.from(Instant.now().plusSeconds(expires)));
    if (registration) claims.claim("purpose","otp-registration");
    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),claims.build());
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }
}
