package com.slokam.av.service;

import com.slokam.av.config.OtpChannelConfig;
import com.slokam.av.dto.*;
import com.slokam.av.entity.*;
import com.slokam.av.exception.custom.ApiException;
import com.slokam.av.repository.UserRepository;
import com.slokam.av.security.JwtService;
import com.slokam.av.util.MobileNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
public class ConfiguredOtpService {
    private final OtpChannelConfig config;
    private final OtpService otp;
    private final UserRepository users;
    private final MobileNormalizer mobile;
    private final JwtService jwt;
    private final String adminMobile, adminEmail;
    public ConfiguredOtpService(OtpChannelConfig config, OtpService otp, UserRepository users,
            MobileNormalizer mobile, JwtService jwt,
            @Value("${app.admin.mobile:+918881178888}") String adminMobile,
            @Value("${app.admin.email:}") String adminEmail) {
        this.config=config; this.otp=otp; this.users=users; this.mobile=mobile; this.jwt=jwt;
        this.adminMobile=adminMobile.isBlank() ? "" : mobile.normalize(adminMobile);
        this.adminEmail=adminEmail.trim().toLowerCase(Locale.ROOT);
    }
    private String email(String raw) {
        String value=raw.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || value.length()>255)
            throw new ApiException(400,"INVALID_EMAIL","Enter a valid email address");
        return value;
    }
    private User loginAccount(String address) {
        User user=users.findByEmailIgnoreCase(address).orElseThrow(() ->
            new ApiException(400,"REGISTRATION_REQUIRED","Complete registration before signing in"));
        if (!user.enabled || (user.role == Role.ADMIN && !address.equals(adminEmail)))
            throw new ApiException(403,"ACCOUNT_UNAUTHORIZED","This account cannot authenticate using this identity");
        return user;
    }
    // Default endpoints are always email login, regardless of the old channel setting.
    public OtpResponse request(String raw) {
        String address=email(raw); loginAccount(address);
        return otp.requestEmail(address);
    }
    @Transactional(noRollbackFor=ApiException.class)
    public OtpResult verify(OtpVerifyRequest request) {
        String address=email(request.destination());
        User user=loginAccount(address);
        Long challenge=otp.verifyChannel(address,request.otp(),"EMAIL");
        otp.consumeChallenge(address,"EMAIL",challenge);
        user.emailVerifiedAt=java.time.Instant.now();
        users.save(user);
        return authenticated(user);
    }
    private String newMobile(String raw) {
        String number=mobile.normalize(raw);
        if (number.equals(adminMobile) || users.findByMobile(number).isPresent())
            throw new ApiException(409,"CONTACT_ALREADY_IN_USE","Use email login for an existing account; this mobile cannot register");
        return number;
    }
    public OtpResponse requestRegistration(String raw) {
        return otp.requestWhatsApp(newMobile(raw));
    }
    @Transactional(noRollbackFor=ApiException.class)
    public OtpResult verifyRegistration(OtpVerifyRequest request) {
        String number=newMobile(request.destination());
        Long challenge=otp.verifyChannel(number,request.otp(),"WHATSAPP");
        return new OtpResult(true,jwt.registrationProof(number,"WHATSAPP",challenge),null,null,null);
    }
    private org.springframework.security.oauth2.jwt.Jwt proof(String token, String channel, String destination) {
        try {
            var proof=jwt.validateRegistration(token);
            if (!channel.equals(proof.getClaimAsString("channel")) ||
                    (destination != null && !destination.equals(proof.getSubject())))
                throw new IllegalArgumentException();
            return proof;
        } catch (org.springframework.security.oauth2.jwt.JwtException | IllegalArgumentException e) {
            throw new ApiException(400,"INVALID_REGISTRATION_TOKEN","Registration verification is invalid or expired");
        }
    }
    private String availableEmail(String raw) {
        String address=email(raw);
        if (address.equals(adminEmail) || users.findByEmailIgnoreCase(address).isPresent())
            throw new ApiException(409,"CONTACT_ALREADY_IN_USE","Email is already in use or reserved");
        return address;
    }
    @Transactional
    public OtpResponse requestRegistrationEmail(RegistrationEmailRequest request) {
        var mobileProof=proof(request.registrationToken(),"WHATSAPP",null);
        otp.requireChallenge(mobileProof.getSubject(),"WHATSAPP",mobileProof.getClaim("challenge"));
        return otp.requestRegistrationEmail(availableEmail(request.email()),request.registrationToken());
    }
    @Transactional(noRollbackFor=ApiException.class)
    public OtpResult verifyRegistrationEmail(RegistrationEmailVerifyRequest request) {
        var mobileProof=proof(request.registrationToken(),"WHATSAPP",null);
        otp.requireChallenge(mobileProof.getSubject(),"WHATSAPP",mobileProof.getClaim("challenge"));
        String address=availableEmail(request.email());
        Long challenge=otp.verifyRegistrationEmail(address,request.otp(),request.registrationToken());
        return new OtpResult(true,jwt.registrationProof(address,"REGISTER_EMAIL",challenge),null,null,null);
    }
    @Transactional
    public OtpResult register(OtpRegistrationRequest request) {
        String number=newMobile(request.mobile()), address=availableEmail(request.email());
        var mobileProof=proof(request.registrationToken(),"WHATSAPP",number);
        var emailProof=proof(request.emailVerificationToken(),"REGISTER_EMAIL",address);
        otp.requireChallenge(number,"WHATSAPP",mobileProof.getClaim("challenge"));
        otp.requireRegistrationBinding(address,request.registrationToken());
        otp.consumeChallenge(number,"WHATSAPP",mobileProof.getClaim("challenge"));
        otp.consumeChallenge(address,"REGISTER_EMAIL",emailProof.getClaim("challenge"));
        User user=new User();
        user.name=request.name().trim(); user.email=address; user.mobile=number;
        user.mobileVerifiedAt=java.time.Instant.now(); user.emailVerifiedAt=user.mobileVerifiedAt;
        return authenticated(users.saveAndFlush(user));
    }
    private OtpResult authenticated(User user) {
        return new OtpResult(false,null,UserResponse.of(user),jwt.generate(user),"Bearer");
    }
}
