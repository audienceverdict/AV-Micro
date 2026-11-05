package com.slokam.av.controller;
import com.slokam.av.entity.User;
import com.slokam.av.repository.UserRepository;
import com.slokam.av.exception.custom.ApiException;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@RestController @RequestMapping("/internal/users")
@Transactional(readOnly=true)
public class InternalUserController {
 private final UserRepository users;
 public InternalUserController(UserRepository users){this.users=users;}
 @GetMapping("/{id}") public User user(@PathVariable String id){return users.findById(id).orElseThrow(()->new ApiException(404,"USER_NOT_FOUND","User not found"));}
 @GetMapping public List<User> enabled(){return users.findByEnabledTrue();}
}
