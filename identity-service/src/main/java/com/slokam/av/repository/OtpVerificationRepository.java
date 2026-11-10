package com.slokam.av.repository;

import com.slokam.av.entity.OtpVerification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findFirstByMobileAndChannelOrderByIdDesc(
            String mobile, String channel);

    long countByMobileAndChannelAndCreatedAtAfter(String mobile, String channel, Instant since);

    java.util.List<OtpVerification> findByMobile(String mobile);

    long deleteByCreatedAtBefore(Instant cutoff);
}
