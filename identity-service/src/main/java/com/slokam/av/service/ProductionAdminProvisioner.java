package com.slokam.av.service;

import com.slokam.av.entity.Role;
import com.slokam.av.entity.User;
import com.slokam.av.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Component
@Profile("prod")
public class ProductionAdminProvisioner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ProductionAdminProvisioner.class);

    private final UserRepository users;
    private final String adminEmail;
    private final String adminMobile;
    private final com.slokam.av.config.OtpChannelConfig channel;

    public ProductionAdminProvisioner(
            UserRepository users, com.slokam.av.config.OtpChannelConfig channel, @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.mobile:}") String adminMobile, com.slokam.av.util.MobileNormalizer normalizer) {
        this.users = users;
        this.adminMobile = adminMobile.isBlank() ? "" : normalizer.normalize(adminMobile);
        this.channel = channel;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank()) {
            log.warn(
                    "Production admin account was not provisioned because app.admin.email is"
                            + " empty");
            return;
        }

        var existing = users.findByEmailIgnoreCase(adminEmail);
        var byMobile=adminMobile.isBlank() ? java.util.Optional.<User>empty() : users.findByMobile(adminMobile);
        if (byMobile.isPresent()) {
            User mobileUser=byMobile.get();
            if (mobileUser.role != Role.ADMIN || (existing.isPresent() && !existing.get().id.equals(mobileUser.id))
                    || (mobileUser.email != null && !mobileUser.email.isBlank() && !mobileUser.email.equalsIgnoreCase(adminEmail)))
                throw new IllegalStateException("Configured administrator contacts conflict; resolve the existing account before startup");
            if (existing.isEmpty()) {
                mobileUser.email=adminEmail;
                users.saveAndFlush(mobileUser);
                existing=java.util.Optional.of(mobileUser);
            }
        }
        User user;
        boolean changed;
        if (existing.isPresent()) {
            user = existing.get();
            changed = false;
        } else {
            user = new User();
            user.name = "Administrator";
            user.email = adminEmail;
            // User.mobile is required and unique in the current schema. Admins authenticate by
            // email OTP.
            user.mobile = "ADMIN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            changed = true;
        }

        if (user.role != Role.ADMIN) {
            user.role = Role.ADMIN;
            changed = true;
        }
        if (!user.enabled) {
            user.enabled = true;
            changed = true;
        }
        if (changed) {
            users.save(user);
        }
        log.info("Provisioned configured production admin account {}", adminEmail);
    }
}
