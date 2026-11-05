package com.slokam.av.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(columnNames = {"screen_id", "label"}))
public class Seat {
    @Id
    @Column(length = 80)
    public String id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "screen_id")
    public Screen screen;

    @Column(nullable = false, length = 80)
    public String label;

    @Column(nullable = false, length = 40)
    public String category = "REGULAR";

    @Column(length = 7)
    public String color = "#8B7A91";

    @Column(nullable = false)
    public boolean disabled = false;

    @Column(name = "`row_number`", nullable = false)
    public int rowNumber;

    @Column(nullable = false)
    public int columnNumber;
}
