package com.slokam.av.security;
import com.slokam.av.client.IdentityClient;
import com.slokam.av.entity.*;
import com.slokam.av.exception.custom.ApiException;
import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.web.client.RestClient;
import org.junit.jupiter.api.*;
import java.net.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class IdentityBoundaryTest {
 HttpServer server;String base;RSAKey key;NimbusJwtEncoder encoder;
 volatile boolean enabled=true;volatile Role role=Role.USER;volatile int accountStatus=200;
 @BeforeEach void start() throws Exception {
  var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
  key=new RSAKey.Builder((java.security.interfaces.RSAPublicKey)pair.getPublic()).privateKey(pair.getPrivate()).keyID("boundary-key").build();
  encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/jwks",exchange->{byte[] body=new JWKSet(key.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
  server.createContext("/internal/users",exchange->{
   int status="boundary-service-token-at-least-32-characters".equals(exchange.getRequestHeaders().getFirst("X-Service-Token"))?accountStatus:401;
   String json="{\"id\":\"viewer\",\"name\":\"Viewer\",\"role\":\""+role+"\",\"enabled\":"+enabled+",\"additionalRoles\":[],\"verifierShowIds\":[]}";
   byte[] body=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();
  });server.start();base="http://127.0.0.1:"+server.getAddress().getPort();
 }
 @AfterEach void stop(){server.stop(0);}
 String token(String issuer,String audience,Instant expiry,String purpose){
  var builder=JwtClaimsSet.builder().subject("viewer").issuer(issuer).audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(10)).expiresAt(expiry);
  if(purpose!=null)builder.claim("purpose",purpose);
  return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build(),builder.build())).getTokenValue();
 }
 @Test void businessValidatesRsaJwksIssuerAudienceExpiryAndTokenPurpose(){
  var verifier=new JwtService(base+"/jwks");var future=Instant.now().plusSeconds(300);
  assertEquals("viewer",verifier.validate(token("audience-verdict","av-api",future,null)).getSubject());
  assertThrows(JwtException.class,()->verifier.validate(token("wrong","av-api",future,null)));
  assertThrows(JwtException.class,()->verifier.validate(token("audience-verdict","other",future,null)));
  assertThrows(JwtException.class,()->verifier.validate(token("audience-verdict","av-api",Instant.now().minusSeconds(1),null)));
  assertThrows(JwtException.class,()->verifier.validate(token("audience-verdict","av-api",future,"otp-registration")));
 }
 @Test void currentRolesAndDisabledStatusComeFromIdentityAndOutagesFailClosed(){
  var client=new IdentityClient(RestClient.builder(),base,"boundary-service-token-at-least-32-characters");
  var details=new CustomUserDetailsService(client);
  assertTrue(details.loadUserByUsername("viewer").isEnabled());
  role=Role.ADMIN;assertTrue(details.loadUserByUsername("viewer").getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")));
  enabled=false;assertFalse(details.loadUserByUsername("viewer").isEnabled());
  accountStatus=404;assertTrue(client.findById("missing").isEmpty());
  accountStatus=503;assertEquals(503,assertThrows(ApiException.class,()->details.loadUserByUsername("viewer")).status);
 }
 @Test void discoveryNotReadyFailsClosedWith503InsteadOfServerError(){
  var builder=RestClient.builder().requestInterceptor((request,body,execution)->{throw new IllegalStateException("No discovery instances");});
  var client=new IdentityClient(builder,base,"boundary-service-token-at-least-32-characters");
  assertEquals(503,assertThrows(ApiException.class,()->client.findById("viewer")).status);
 }

}
