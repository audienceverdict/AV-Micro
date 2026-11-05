package com.slokam.av.dto;
import jakarta.validation.constraints.*;
public record OtpRegistrationRequest(@NotBlank @Size(max=4096) String registrationToken,
        @NotBlank @Size(max=150) String name,
        @NotBlank @Email @Size(max=255) String email,
        @NotBlank @Size(max=25) String mobile,
        @NotBlank @Size(max=4096) String emailVerificationToken) {
    public OtpRegistrationRequest(String token, String name, String email, String mobile) {
        this(token,name,email,mobile,null);
    }
}
