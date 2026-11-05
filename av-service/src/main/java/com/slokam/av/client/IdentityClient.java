package com.slokam.av.client;
import com.slokam.av.entity.User;
import com.slokam.av.exception.custom.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.util.*;
@Component
public class IdentityClient {
 private final RestClient client;
 public IdentityClient(RestClient.Builder builder,@Value("${internal.identity-url:http://identity-service}") String url,@Value("${internal.service-token}") String token){client=builder.baseUrl(url).defaultHeader("X-Service-Token",token).build();}
 public Optional<User> findById(String id){
  try{return Optional.ofNullable(client.get().uri("/internal/users/{id}",id).retrieve().body(User.class));}
  catch(HttpClientErrorException.NotFound e){return Optional.empty();}
  catch(RestClientException | IllegalStateException e){throw unavailable();}
 }
 public List<User> findByEnabledTrue(){
  try{var rows=client.get().uri("/internal/users").retrieve().body(User[].class);return rows==null?List.of():Arrays.asList(rows);}
  catch(RestClientException | IllegalStateException e){throw unavailable();}
 }
 private ApiException unavailable(){return new ApiException(503,"IDENTITY_UNAVAILABLE","Account service is temporarily unavailable");}
}
