package com.slokam.av.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "otp_verifications")
public class OtpVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 255)
    public String mobile;

    @Column(nullable = false, length = 16)
    public String channel = "EMAIL";

    @Column(length = 4096)
    public String registrationBinding;

    @Column(nullable = false)
    public String otpHash;

    @Column(nullable = false)
    public Instant expiresAt;

    @Column(nullable = false)
    public int attemptCount;

    @Column(nullable = false)
    public boolean verified;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();
}
