package com.slokam.av.security;

import com.slokam.av.repository.UserRepository;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public CustomUserDetailsService(UserRepository users) {
        this.users = users;
    }

    public UserDetails loadUserByUsername(String id) {
        var u =
                users.findById(id)
                        .orElseThrow(() -> new UsernameNotFoundException("Account unavailable"));
        return org.springframework.security.core.userdetails.User.withUsername(u.id)
                .password("")
                .roles(u.effectiveRoles().stream().map(Enum::name).toArray(String[]::new))
                .disabled(!u.enabled)
                .build();
    }
}
