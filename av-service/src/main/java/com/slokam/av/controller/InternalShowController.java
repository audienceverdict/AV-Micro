package com.slokam.av.controller;
import com.slokam.av.repository.ShowRepository;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/shows")
public class InternalShowController {
 private final ShowRepository shows;
 public InternalShowController(ShowRepository shows){this.shows=shows;}
 @GetMapping("/{id}/exists") public boolean exists(@PathVariable String id){return shows.existsById(id);}
}
