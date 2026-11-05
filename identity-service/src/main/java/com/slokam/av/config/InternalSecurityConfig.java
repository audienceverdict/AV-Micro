package com.slokam.av.config;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration
public class InternalSecurityConfig {
 @Bean @Order(0)
 SecurityFilterChain internalSecurity(HttpSecurity http,@Value("${internal.service-token}") String token) throws Exception {
  if(token.length()<32)throw new IllegalArgumentException("Service token must contain at least 32 characters");
  var filter=new OncePerRequestFilter(){
   protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,jakarta.servlet.FilterChain chain) throws java.io.IOException,jakarta.servlet.ServletException {
    var supplied=req.getHeader("X-Service-Token");
    if(supplied==null||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){res.sendError(401);return;}
    chain.doFilter(req,res);
   }
  };
  return http.securityMatcher("/internal/**").csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.anyRequest().permitAll()).addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
 }
}
