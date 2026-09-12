package com.example.barbershop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "blocked_times")
public class BlockedTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "barber_id", nullable = false)
    private Barber barber;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    private String reason;

    protected BlockedTime() {
    }

    public BlockedTime(
            Barber barber,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String reason
    ) {
        this.barber = barber;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Barber getBarber() {
        return barber;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getReason() {
        return reason;
    }
}
