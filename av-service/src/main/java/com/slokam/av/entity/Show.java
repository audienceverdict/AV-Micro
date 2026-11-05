package com.slokam.av.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "shows")
public class Show {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 36)
    public String movieId;

    @Column(nullable = false, length = 36)
    public String theatreId;

    @Column(nullable = false, length = 36)
    public String screenId;

    @Column(nullable = false)
    public LocalDate date;

    @Column(nullable = false)
    public LocalTime startTime;

    @Column(nullable = false)
    public LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public TicketType ticketType = TicketType.PAID;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal ticketPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    public int maxTicketsPerMobile = 6;

    @Column(nullable = false)
    public int holdMinutes = 5;

    @Column(nullable = false)
    public int layoutVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    public Map<String, BigDecimal> seatPrices = new java.util.HashMap<>();

    @Column(name = "layout_version_id", length = 36)
    public String layoutVersionId;

    @Column(nullable = false)
    public boolean requireAdminConfirmation = false;

    public Instant bookingOpens;
    public Instant bookingCloses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ShowStatus status = ShowStatus.OPEN;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
