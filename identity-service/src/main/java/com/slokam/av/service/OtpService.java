package com.slokam.av.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.slokam.av.dto.OtpResponse;
import com.slokam.av.entity.OtpVerification;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.AuthLockRepository;
import com.slokam.av.repository.OtpVerificationRepository;
import com.slokam.av.util.BrandedEmailTemplate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private final OtpVerificationRepository records;
    private final AuthLockRepository locks;
    private final com.slokam.av.service.EmailDeliveryService email;
    private final BCryptPasswordEncoder hashes = new BCryptPasswordEncoder();
    private final MetaWhatsAppProvider whatsapp;
    private final SecureRandom random = new SecureRandom();
    private final long expiry, cooldown;
    private final int attempts, requests;

    public OtpService(
            OtpVerificationRepository records,
            AuthLockRepository locks,
            com.slokam.av.service.EmailDeliveryService email,
            MetaWhatsAppProvider whatsapp,
            @Value("${auth.otp.expiration-seconds:600}") long expiry,
            @Value("${auth.otp.resend-cooldown-seconds:30}") long cooldown,
            @Value("${auth.otp.max-attempts:5}") int attempts,
            @Value("${auth.otp.max-requests-per-hour:10}") int requests) {
        this.records = records;
        this.locks = locks;
        this.email = email;
        this.whatsapp = whatsapp;
        this.expiry = expiry;
        this.cooldown = cooldown;
        this.attempts = attempts;
        this.requests = requests;
        if (expiry < 1 || cooldown < 1 || attempts < 1 || requests < 1)
            throw new IllegalArgumentException("OTP limits must be positive");
    }

    private void lock(String destination) {
        locks.lock(Math.floorMod(destination.hashCode(), 64));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public OtpResponse requestEmail(String address) {
        log.debug("Processing OtpService.requestEmail");
        try {
            return requestCode(address, "EMAIL");
        } catch (ApiException e) {
            if (e.status >= 500)
                org.springframework.transaction.interceptor.TransactionAspectSupport
                        .currentTransactionStatus()
                        .setRollbackOnly();
            throw e;
        }
    }

    @Transactional
    public OtpResponse requestWhatsApp(String mobile) {
        return requestCode(mobile, "WHATSAPP");
    }

    @Transactional
    public OtpResponse requestRegistrationEmail(String address, String binding) {
        OtpResponse response=requestCode(address,"REGISTER_EMAIL");
        var row=records.findFirstByMobileAndChannelOrderByIdDesc(address,"REGISTER_EMAIL").orElseThrow();
        row.registrationBinding=bindingHash(binding);
        records.save(row);
        return response;
    }

    private String bindingHash(String token) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public void requireRegistrationBinding(String address, String binding) {
        lock(address);
        var row=records.findFirstByMobileAndChannelOrderByIdDesc(address,"REGISTER_EMAIL").orElseThrow(this::invalid);
        if (!bindingHash(binding).equals(row.registrationBinding)) throw invalid();
    }

    public Long verifyRegistrationEmail(String address, String code, String binding) {
        requireRegistrationBinding(address,binding);
        return verifyChannel(address,code,"REGISTER_EMAIL");
    }

    public void requireChallenge(String destination, String channel, Number challenge) {
        lock(destination);
        var row=records.findFirstByMobileAndChannelOrderByIdDesc(destination,channel).orElseThrow(this::invalid);
        if (row.id.longValue()!=challenge.longValue() || !row.verified || !row.expiresAt.isAfter(Instant.now())) throw invalid();
    }

    private OtpResponse requestCode(String destination, String channel) {
        lock(destination);
        Instant now = Instant.now();
        var latest = records.findFirstByMobileAndChannelOrderByIdDesc(destination, channel);
        if (latest.isPresent() && latest.get().createdAt.plusSeconds(cooldown).isAfter(now))
            throw new ApiException(
                    429, "OTP_COOLDOWN", "Please wait before requesting another code");
        if (records.countByMobileAndChannelAndCreatedAtAfter(
                        destination, channel, now.minusSeconds(3600))
                >= requests)
            throw new ApiException(
                    429, "OTP_RATE_LIMIT", "Too many requests. Please try again later.");
        if (latest.isPresent() && latest.get().verified) latest.get().verified = false;
        String code = String.format("%06d", random.nextInt(1000000));
        OtpVerification otp = new OtpVerification();
        otp.mobile = destination;
        otp.channel = channel;
        otp.otpHash = hashes.encode(code);
        otp.createdAt = now;
        otp.expiresAt = now.plusSeconds(expiry);
        records.saveAndFlush(otp);
        if (channel.equals("WHATSAPP")) {
            whatsapp.sendOtp(destination, code);
        } else {
            var message = BrandedEmailTemplate.otp(code, expiry / 60);
            email.sendHtml(
                    destination,
                    "Your Audience Verdict sign-in code",
                    message.plainText(),
                    message.html(),
                    null);
        }
        log.info("Verification code dispatched channel={}", channel);
        return new OtpResponse(
                true,
                channel.equals("WHATSAPP")
                        ? "WhatsApp OTP accepted by Meta; delivery is not yet confirmed"
                        : "Verification code queued for email delivery",
                expiry,
                cooldown);
    }

    // Called inside AuthService's transaction. Invalid attempts must commit as well.
    public void verifyEmail(String address, String code) {
        log.debug("Processing OtpService.verifyEmail");
        verifyChannel(address, code, "EMAIL");
        log.debug("Email verification succeeded");
    }

    public Long verifyChannel(String destination, String code, String channel) {
        lock(destination);
        var otp =
                records.findFirstByMobileAndChannelOrderByIdDesc(destination, channel)
                        .orElseThrow(() -> invalid());
        if (otp.verified || !otp.expiresAt.isAfter(Instant.now())) throw invalid();
        if (otp.attemptCount >= attempts)
            throw new ApiException(
                    429, "OTP_ATTEMPT_LIMIT", "Too many attempts. Request a new code.");
        otp.attemptCount++;
        records.save(otp);
        if (!hashes.matches(code, otp.otpHash)) throw invalid();
        otp.verified = true;
        records.save(otp);
        return otp.id;
    }

    @Transactional
    public void requireVerifiedEmail(String address) {
        log.debug("Processing OtpService.requireVerifiedEmail");
        lock(address);
        var otp =
                records.findFirstByMobileAndChannelOrderByIdDesc(address, "EMAIL")
                        .orElseThrow(() -> invalid());
        if (!otp.verified || !otp.expiresAt.isAfter(Instant.now())) throw invalid();
    }

    @Transactional
    public void consumeVerifiedEmail(String address) {
        log.debug("Processing OtpService.consumeVerifiedEmail");
        requireVerifiedEmail(address);
        var otp =
                records.findFirstByMobileAndChannelOrderByIdDesc(address, "EMAIL")
                        .orElseThrow(() -> invalid());
        otp.verified = false;
        records.save(otp);
    }

    public void consumeChallenge(String destination, String channel, Long challengeId) {
        lock(destination);
        var row = records.findFirstByMobileAndChannelOrderByIdDesc(destination, channel)
                .orElseThrow(this::invalid);
        if (!row.id.equals(challengeId) || !row.verified || !row.expiresAt.isAfter(Instant.now()))
            throw invalid();
        row.verified = false;
        row.expiresAt = Instant.EPOCH;
        records.save(row);
    }

    private ApiException invalid() {
        log.debug("OTP verification rejected: invalid or expired code");
        return new ApiException(400, "INVALID_OTP", "The code is invalid or expired");
    }

    // Keep request history beyond the hourly rate-limit window, even for expired codes.
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void cleanup() {
        log.debug("Processing OtpService.cleanup");
        records.deleteByCreatedAtBefore(Instant.now().minusSeconds(Math.max(86400, expiry + 3600)));
    }
}
