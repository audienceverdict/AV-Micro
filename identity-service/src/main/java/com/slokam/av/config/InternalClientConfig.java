package com.slokam.av.config;
import org.springframework.context.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestClient;
@Configuration
public class InternalClientConfig {
 @Bean @LoadBalanced RestClient.Builder internalRestClientBuilder() {
  var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
  factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
  return RestClient.builder().requestFactory(factory);
 }
}
