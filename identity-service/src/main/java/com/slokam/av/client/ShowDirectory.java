package com.slokam.av.client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import com.slokam.av.exception.custom.ApiException;
@Component
public class ShowDirectory {
 private final RestClient client;
 public ShowDirectory(RestClient.Builder builder,@Value("${internal.av-url:http://av-service}") String url,@Value("${internal.service-token}") String token){client=builder.baseUrl(url).defaultHeader("X-Service-Token",token).build();}
 public boolean existsById(String id){
  try{return Boolean.TRUE.equals(client.get().uri("/internal/shows/{id}/exists",id).retrieve().body(Boolean.class));}
  catch(RestClientException | IllegalStateException e){throw new ApiException(503,"SHOW_SERVICE_UNAVAILABLE","Show validation is temporarily unavailable");}
 }
}
