package com.slokam.platform;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
@Component
public class RequestIdFilter implements GlobalFilter,Ordered {
 public int getOrder(){return -100;}
 public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain){
  String id=java.util.UUID.randomUUID().toString();
  exchange.getResponse().getHeaders().set("X-Request-ID",id);
  return chain.filter(exchange.mutate().request(r->r.headers(h->{h.set("X-Request-ID",id);h.remove("X-Service-Token");h.remove("X-User-ID");h.remove("X-Roles");})).build());
 }
}
