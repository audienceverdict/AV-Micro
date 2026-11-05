package com.slokam.av.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.slokam.av.dto.EmailOtpRegistrationRequest;
import com.slokam.av.dto.EmailOtpRequest;
import com.slokam.av.dto.EmailOtpResult;
import com.slokam.av.dto.EmailOtpVerifyRequest;
import com.slokam.av.dto.OtpResponse;
import com.slokam.av.dto.UpdateProfileRequest;
import com.slokam.av.dto.UserResponse;
import com.slokam.av.entity.Role;
import com.slokam.av.entity.User;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.UserRepository;
import com.slokam.av.security.JwtService;
import com.slokam.av.util.MobileNormalizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository users;
    private final OtpService otp;
    private final com.slokam.av.config.OtpChannelConfig channel;
    private final MobileNormalizer mobile;
    private final JwtService jwt;
    private final String adminEmail;

    public AuthService(
            UserRepository users,
            OtpService otp,
            com.slokam.av.config.OtpChannelConfig channel,
            MobileNormalizer mobile,
            JwtService jwt,
            @Value("${app.admin.email:}") String adminEmail) {
        this.users = users;
        this.otp = otp;
        this.channel = channel;
        this.mobile = mobile;
        this.jwt = jwt;
        this.adminEmail = normalize(adminEmail);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private User account(String address) {
        return users.findByEmailIgnoreCase(address).orElse(null);
    }

    private void requireAuthorizedAdmin(String address) {
        User existing = account(address);
        if (existing != null && existing.role == Role.ADMIN && !address.equals(adminEmail))
            throw new ApiException(
                    403,
                    "ADMIN_EMAIL_UNAUTHORIZED",
                    "This email is not authorized for admin login.");
    }

    public OtpResponse requestEmail(EmailOtpRequest r) {
        channel.requireEmail();
        log.debug("Processing AuthService.requestEmail");
        String address = normalize(r.email());
        requireAuthorizedAdmin(address);
        return otp.requestEmail(address);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public EmailOtpResult verifyEmail(EmailOtpVerifyRequest r) {
        channel.requireEmail();
        log.debug("Processing AuthService.verifyEmail");
        String address = normalize(r.email());
        User existing = account(address);
        boolean reservedAdmin = !adminEmail.isBlank() && adminEmail.equals(address);
        if ((reservedAdmin && existing == null)
                || (existing != null && existing.role == Role.ADMIN && !reservedAdmin))
            throw new ApiException(
                    403,
                    "ADMIN_EMAIL_UNAUTHORIZED",
                    "This email is not authorized for admin login.");
        otp.verifyEmail(address, r.otp());
        existing = account(address);
        if (existing != null && !existing.enabled)
            throw new ApiException(401, "UNAUTHORIZED", "Unable to authenticate this account");
        if (existing == null
                || existing.name == null
                || existing.name.isBlank()
                || existing.name.equals("Movie lover")
                || existing.mobile == null
                || existing.mobile.isBlank()) return new EmailOtpResult(true, null, null, null);
        if (reservedAdmin && existing.role != Role.ADMIN) {
            existing.role = Role.ADMIN;
            existing = users.saveAndFlush(existing);
        }
        log.info("User authenticated userId={}", existing.id);
        return authenticated(existing);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public EmailOtpResult registerEmail(EmailOtpRegistrationRequest r) {
        channel.requireEmail();
        log.debug("Processing AuthService.registerEmail");
        String address = normalize(r.email());
        if (!adminEmail.isBlank() && adminEmail.equals(address))
            throw new ApiException(
                    403,
                    "ADMIN_EMAIL_UNAUTHORIZED",
                    "This email is not authorized for admin login.");
        requireAuthorizedAdmin(address);
        otp.requireVerifiedEmail(address);
        String number = mobile.normalize(r.mobile());
        var emailUser = account(address);
        var numberUser = users.findByMobile(number).orElse(null);
        if (numberUser != null && emailUser != null && !numberUser.id.equals(emailUser.id))
            throw new ApiException(409, "DUPLICATE_MOBILE", "This mobile number is already in use");
        if (numberUser != null
                && emailUser == null
                && numberUser.email != null
                && !numberUser.email.isBlank())
            throw new ApiException(409, "DUPLICATE_MOBILE", "This mobile number is already in use");
        var user = emailUser != null ? emailUser : numberUser != null ? numberUser : new User();
        if (!user.enabled)
            throw new ApiException(401, "UNAUTHORIZED", "Unable to authenticate this account");
        user.name = r.name().trim();
        user.mobile = number;
        user.email = address;
        user = users.saveAndFlush(user);
        otp.consumeVerifiedEmail(address);
        log.info("User registered userId={}", user.id);
        return authenticated(user);
    }

    private EmailOtpResult authenticated(User user) {
        return new EmailOtpResult(false, UserResponse.of(user), jwt.generate(user), "Bearer");
    }

    public User get(String id) {
        log.debug("Processing AuthService.get");
        return users.findById(id)
                .orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND", "User not found"));
    }

    public UserResponse current(String id) {
        log.debug("Processing AuthService.current");
        return UserResponse.of(get(id));
    }

    @Transactional
    public UserResponse update(String id, UpdateProfileRequest r) {
        log.debug("Processing AuthService.update");
        var u = get(id);
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equalsIgnoreCase(u.email))
            throw new ApiException(
                    400,
                    "EMAIL_CHANGE_REQUIRES_VERIFICATION",
                    "Changing your sign-in email requires email verification");
        u.name = r.name().trim();
        return UserResponse.of(users.saveAndFlush(u));
    }

    public Page<UserResponse> list(int page, int size) {
        log.debug("Processing AuthService.list");
        return users.findAll(
                        PageRequest.of(
                                Math.max(0, page),
                                Math.max(1, Math.min(size, 100)),
                                Sort.by("createdAt").descending()))
                .map(UserResponse::of);
    }

    @Transactional
    public UserResponse role(String id, Role role) {
        log.debug("Processing AuthService.role");
        var u = get(id);
        if (role != Role.USER && role != Role.ADMIN)
            throw new ApiException(400, "INVALID_PRIMARY_ROLE", "Use the additional roles endpoint for staff roles");
        u.role = role;
        log.info("User role updated userId={} role={}", id, role);
        return UserResponse.of(users.save(u));
    }

    @Transactional
    public UserResponse status(String id, boolean enabled) {
        log.debug("Processing AuthService.status");
        var u = get(id);
        u.enabled = enabled;
        log.info("User account status updated userId={} enabled={}", id, enabled);
        return UserResponse.of(users.save(u));
    }
}
