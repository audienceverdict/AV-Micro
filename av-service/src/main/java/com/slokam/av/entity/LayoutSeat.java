package com.slokam.av.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(
        name = "layout_seats",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_layout_seat_identity", columnNames = {"layout_version_id", "seat_id"}),
            @UniqueConstraint(
                    name = "uk_layout_seat_label",
                    columnNames = {"layout_version_id", "label"}),
            @UniqueConstraint(
                    name = "uk_layout_seat_position",
                    columnNames = {"layout_version_id", "`row_number`", "column_number"})
        })
public class LayoutSeat {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Id
    @Column(length = 36)
    public String id = UUID.randomUUID().toString();

    // Additive identity: existing row IDs remain intact; booking IDs survive version copies.
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(length = 80)
    public String seatId;

    @com.fasterxml.jackson.annotation.JsonGetter("id")
    public String bookingSeatId() { return seatId == null ? id : seatId; }

    @com.fasterxml.jackson.annotation.JsonSetter("id")
    public void readSeatId(String value) { seatId = value; }

    @Column(name = "layout_version_id", nullable = false, length = 36)
    public String layoutVersionId;

    @Column(nullable = false, length = 80)
    public String label;

    @Column(name = "`row_number`", nullable = false)
    public int rowNumber;

    @Column(name = "column_number", nullable = false)
    public int columnNumber;

    @Column(nullable = false, length = 40)
    public String category = "REGULAR";

    @Column(nullable = false)
    public boolean disabled;

    @Column(length = 7)
    public String color;
}
