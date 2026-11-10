package com.slokam.av.service;

import com.slokam.av.entity.User;
import com.slokam.av.entity.Role;
import com.slokam.av.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.util.Locale;

/** Opt-in demo login; never grants roles or changes an existing account. */
public class DemoAccount implements ApplicationRunner {
    private final boolean enabled;
    private final String email;
    private final String code;
    private final UserRepository users;

    public DemoAccount(UserRepository users, Environment environment,
            @Value("${auth.demo.enabled:false}") boolean enabled,
            @Value("${auth.demo.email:demo@example.test}") String email,
            @Value("${auth.demo.otp:123456}") String code) {
        this.users = users;
        this.enabled = enabled;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.code = code;
        if (enabled) {
            if (environment.acceptsProfiles(Profiles.of("prod", "production"))
                    || !environment.acceptsProfiles(Profiles.of("dev", "local", "test", "otp-test")))
                throw new IllegalArgumentException("Demo login requires a dev, local, test or otp-test profile and cannot run in production");
            if (!this.email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || this.email.length() > 255)
                throw new IllegalArgumentException("Demo email must be a valid email address");
            if (!code.matches("[0-9]{6}"))
                throw new IllegalArgumentException("Demo OTP must contain six digits");
        }
    }

    public boolean matches(String destination, String channel) {
        return enabled && "EMAIL".equals(channel) && email.equalsIgnoreCase(destination.trim());
    }

    public String code() { return code; }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        var existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (!user.enabled || user.deletedAt != null || user.role != Role.USER || !user.additionalRoles.isEmpty())
                throw new IllegalStateException("Demo email must belong to an enabled, ordinary user account");
            return;
        }
        User user = new User();
        user.name = "Demo User";
        user.email = email;
        users.saveAndFlush(user);
    }
}
