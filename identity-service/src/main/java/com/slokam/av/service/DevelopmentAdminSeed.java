package com.slokam.av.service;

import com.slokam.av.entity.Role;
import com.slokam.av.entity.User;
import com.slokam.av.repository.UserRepository;
import com.slokam.av.util.MobileNormalizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevelopmentAdminSeed implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentAdminSeed.class);
    private final UserRepository users;
    private final MobileNormalizer normalizer;
    private final String mobile, email;

    public DevelopmentAdminSeed(
            UserRepository users,
            MobileNormalizer normalizer,
            @Value("${app.admin.mobile:}") String mobile,
            @Value("${app.admin.email:}") String email) {
        this.users = users;
        this.normalizer = normalizer;
        this.mobile = mobile;
        this.email = email;
    }

    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank()) {
            if (!mobile.isBlank())
                log.warn(
                        "ADMIN_MOBILE is configured but ADMIN_EMAIL is missing; development admin"
                                + " seed was skipped");
            return;
        }
        String address = email.trim().toLowerCase(java.util.Locale.ROOT);
        var emailUser = users.findByEmailIgnoreCase(address).orElse(null);
        if (mobile.isBlank()) {
            if (emailUser == null) {
                log.warn(
                        "Configured development admin email {} has no user account; sign in once as"
                                + " a user or configure app.admin.mobile",
                        address);
                return;
            }
            emailUser.role = Role.ADMIN;
            emailUser.enabled = true;
            users.save(emailUser);
            log.info("Enabled configured development admin account {}", address);
            return;
        }
        String number = normalizer.normalize(mobile);
        var u =
                users.findByMobile(number)
                        .orElseGet(
                                () -> {
                                    var fresh = new User();
                                    fresh.mobile = number;
                                    fresh.name = "Administrator";
                                    return fresh;
                                });
        if (emailUser != null && !emailUser.id.equals(u.id))
            throw new IllegalStateException("ADMIN_EMAIL is already assigned to another account");
        u.email = address;
        u.role = Role.ADMIN;
        u.enabled = true;
        users.save(u);
    }
}
