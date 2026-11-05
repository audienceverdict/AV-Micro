package com.slokam.av.service;
import com.slokam.av.client.ShowDirectory;
import com.slokam.av.entity.*;
import com.slokam.av.repository.UserRepository;
import com.slokam.av.security.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:identity-http;MODE=MySQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop",
 "spring.flyway.enabled=false","app.admin.email=","app.admin.mobile=","auth.otp.channel=WHATSAPP",
 "app.cors-origins=http://localhost:5173","notifications.email-from=test@example.test"})
class IdentityHttpIntegrationTest {
 @Autowired TestRestTemplate http;
 @Autowired UserRepository users;
 @Autowired JwtService jwt;
 @MockitoBean MetaWhatsAppProvider whatsapp;
 @MockitoBean EmailDeliveryService email;
 @MockitoBean ShowDirectory shows;
 private HttpHeaders bearer(String token){var headers=new HttpHeaders();headers.setBearerAuth(token);return headers;}
 private Map<?,?> post(String path,Object body){var r=http.postForEntity(path,body,Map.class);assertEquals(200,r.getStatusCode().value(),r.toString());return r.getBody();}
 private String emailCode(String address){
  var capture=org.mockito.ArgumentCaptor.forClass(String.class);
  verify(email,atLeastOnce()).sendHtml(eq(address),anyString(),capture.capture(),anyString(),isNull());
  var matcher=java.util.regex.Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(capture.getValue());assertTrue(matcher.find());return matcher.group();
 }
 @Test void registrationEmailLoginAndImmediateAccountChangesWorkOverHttp(){
  String mobile="9000000021",address="http-viewer@example.test";
  post("/api/v1/auth/otp/registration/request",Map.of("destination",mobile));
  var capture=org.mockito.ArgumentCaptor.forClass(String.class);verify(whatsapp).sendOtp(eq("+91"+mobile),capture.capture());
  String proof=(String)post("/api/v1/auth/otp/registration/verify",Map.of("destination",mobile,"otp",capture.getValue())).get("registrationToken");
  post("/api/v1/auth/otp/registration/email/request",Map.of("registrationToken",proof,"email",address));
  String emailProof=(String)post("/api/v1/auth/otp/registration/email/verify",Map.of("registrationToken",proof,"email",address,"otp",emailCode(address))).get("registrationToken");
  var result=post("/api/v1/auth/otp/register",Map.of("registrationToken",proof,"emailVerificationToken",emailProof,"name","HTTP Viewer","email",address,"mobile",mobile));
  String token=(String)result.get("accessToken");String id=(String)((Map<?,?>)result.get("user")).get("id");
  assertEquals(id,jwt.validate(token).getSubject());
  assertEquals(200,http.exchange("/api/v1/auth/me",HttpMethod.GET,new HttpEntity<>(bearer(token)),Map.class).getStatusCode().value());
  assertEquals(401,http.exchange("/api/v1/auth/me",HttpMethod.GET,new HttpEntity<>(bearer(proof)),Map.class).getStatusCode().value());
  reset(whatsapp,email);
  post("/api/v1/auth/otp/request",Map.of("destination",address));
  var login=post("/api/v1/auth/otp/verify",Map.of("destination",address,"otp",emailCode(address)));
  assertEquals(id,((Map<?,?>)login.get("user")).get("id"));verifyNoInteractions(whatsapp);
  var user=users.findById(id).orElseThrow();user.enabled=false;users.saveAndFlush(user);
  assertEquals(401,http.exchange("/api/v1/auth/me",HttpMethod.GET,new HttpEntity<>(bearer(token)),Map.class).getStatusCode().value());
  assertEquals(401,http.getForEntity("/internal/users/"+id,String.class).getStatusCode().value());
  var internal=new HttpHeaders();internal.set("X-Service-Token","test-service-token-at-least-thirty-two-characters");
  assertEquals(200,http.exchange("/internal/users/"+id,HttpMethod.GET,new HttpEntity<>(internal),Map.class).getStatusCode().value());
  var keys=http.getForEntity("/.well-known/jwks.json",Map.class);assertEquals(200,keys.getStatusCode().value());
  var publicKey=(Map<?,?>)((List<?>)keys.getBody().get("keys")).getFirst();
  for(String field:List.of("d","p","q","dp","dq","qi"))assertFalse(publicKey.containsKey(field),"JWKS must not expose private key material");
 }
 @Test void roleUpdatesValidateShowsBeforePersisting(){
  User admin=new User();admin.mobile="+919000000031";admin.email="admin-http@example.test";admin.role=Role.ADMIN;admin=users.saveAndFlush(admin);
  User viewer=new User();viewer.mobile="+919000000032";viewer.email="viewer-role@example.test";viewer=users.saveAndFlush(viewer);
  var headers=bearer(jwt.generate(admin));
  var body=Map.of("roles",List.of("VERIFIER"),"verifierShowIds",List.of("show-http"));
  var path="/api/v1/admin/users/"+viewer.id+"/additional-roles";
  assertEquals(404,http.exchange(path,HttpMethod.PUT,new HttpEntity<>(body,headers),Map.class).getStatusCode().value());
  assertTrue(users.findById(viewer.id).orElseThrow().additionalRoles.isEmpty());
  when(shows.existsById("show-http")).thenReturn(true);
  assertEquals(200,http.exchange(path,HttpMethod.PUT,new HttpEntity<>(body,headers),Map.class).getStatusCode().value());
  assertTrue(users.findById(viewer.id).orElseThrow().effectiveRoles().contains(Role.VERIFIER));
 }
}
