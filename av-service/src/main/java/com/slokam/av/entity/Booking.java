package com.slokam.av.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 36)
    public String userId;

    @Column(nullable = false, length = 36)
    public String showId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_seats", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "seat_id", length = 80)
    public List<String> seatIds = new ArrayList<>();

    @Column(nullable = false)
    public int ticketCount;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public BookingStatus status = BookingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ConfirmationStatus confirmationStatus = ConfirmationStatus.PENDING;

    @Column(nullable = false)
    public boolean attended = false;

    @Column(nullable = false)
    public String movie;

    @Column(nullable = false)
    public String theatre;

    @Column(nullable = false)
    public String screen;

    @Column(nullable = false, length = 30)
    public String date;

    @Column(nullable = false, length = 30)
    public String time;

    @Column(nullable = false, length = 30)
    public String mobile;

    @Column(length = 180)
    public String email;

    @Column(length = 500)
    public String notification;

    @Column(length = 500)
    public String cancellationReason;

    @Column(length = 32, unique = true)
    public String ticketCode =
            UUID.randomUUID().toString().replace("-", "").toUpperCase();

    @Column(nullable = false)
    public Instant createdAt = Instant.now();

    @Column(nullable = false)
    public Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
