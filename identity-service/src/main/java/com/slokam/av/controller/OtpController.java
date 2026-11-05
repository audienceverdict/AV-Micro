package com.slokam.av.controller;
import com.slokam.av.config.OtpChannelConfig;
import com.slokam.av.dto.*;
import com.slokam.av.service.ConfiguredOtpService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/otp")
@io.swagger.v3.oas.annotations.tags.Tag(name="Auth")
public class OtpController {
    private final ConfiguredOtpService service;
    private final OtpChannelConfig config;
    public OtpController(ConfiguredOtpService service, OtpChannelConfig config) {
        this.service=service; this.config=config;
    }
    @io.swagger.v3.oas.annotations.Operation(summary="Get OTP channels by purpose", description="Login uses email; registration verifies WhatsApp and email.")
    @GetMapping("/config")
    public Map<String,String> config() { return Map.of("channel","EMAIL","loginChannel","EMAIL","registrationChannel","WHATSAPP","registrationEmailVerificationRequired","true"); }
    @io.swagger.v3.oas.annotations.Operation(summary="Request an email login OTP", description="Existing accounts only; destination is the registered email.")
    @PostMapping("/request")
    public OtpResponse request(@Valid @RequestBody OtpRequest request) { return service.request(request.destination()); }
    @io.swagger.v3.oas.annotations.Operation(summary="Verify an OTP", description="Verifies email login and returns an access token.")
    @PostMapping("/verify")
    public OtpResult verify(@Valid @RequestBody OtpVerifyRequest request) { return service.verify(request); }
    @io.swagger.v3.oas.annotations.Operation(summary="Complete OTP registration", description="Requires mobile registrationToken and emailVerificationToken from the registration verification endpoints.")
    @PostMapping("/register")
    public OtpResult register(@Valid @RequestBody OtpRegistrationRequest request) { return service.register(request); }
    @io.swagger.v3.oas.annotations.Operation(summary="Request WhatsApp registration OTP", description="New mobile numbers only; returning users must log in with email.")
    @PostMapping("/registration/request")
    public OtpResponse requestRegistration(@Valid @RequestBody OtpRequest request) { return service.requestRegistration(request.destination()); }
    @io.swagger.v3.oas.annotations.Operation(summary="Verify WhatsApp registration OTP", description="Returns a mobile registrationToken; does not authenticate or create an account.")
    @PostMapping("/registration/verify")
    public OtpResult verifyRegistration(@Valid @RequestBody OtpVerifyRequest request) { return service.verifyRegistration(request); }
    @io.swagger.v3.oas.annotations.Operation(summary="Request registration email OTP", description="Requires the mobile registrationToken and the new account email.")
    @PostMapping("/registration/email/request")
    public OtpResponse requestRegistrationEmail(@Valid @RequestBody RegistrationEmailRequest request) { return service.requestRegistrationEmail(request); }
    @io.swagger.v3.oas.annotations.Operation(summary="Verify registration email OTP", description="Returns the email proof as registrationToken; submit it as emailVerificationToken to /register.")
    @PostMapping("/registration/email/verify")
    public OtpResult verifyRegistrationEmail(@Valid @RequestBody RegistrationEmailVerifyRequest request) { return service.verifyRegistrationEmail(request); }
}
