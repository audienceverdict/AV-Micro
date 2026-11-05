package com.slokam.av.controller;
import com.slokam.av.security.JwtService;
import org.springframework.web.bind.annotation.*;
@RestController
public class JwksController {
 private final JwtService jwt;
 public JwksController(JwtService jwt){this.jwt=jwt;}
 @GetMapping("/.well-known/jwks.json") public java.util.Map<String,Object> keys(){return jwt.jwks();}
}
