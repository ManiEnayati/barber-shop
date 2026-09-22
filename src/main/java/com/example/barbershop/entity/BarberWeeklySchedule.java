package com.example.barbershop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "barber_weekly_schedules", uniqueConstraints =
        @UniqueConstraint(columnNames = {"barber_id", "day_of_week"}))
public class BarberWeeklySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barber_id", nullable = false)
    private Barber barber;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected BarberWeeklySchedule() {
    }

    public BarberWeeklySchedule(Barber barber, DayOfWeek dayOfWeek,
                                LocalTime startTime, LocalTime endTime, boolean active) {
        this.barber = barber;
        this.dayOfWeek = dayOfWeek;
        this.createdAt = Instant.now();
        update(startTime, endTime, active);
    }

    public void update(LocalTime startTime, LocalTime endTime, boolean active) {
        this.active = active;
        this.startTime = active ? startTime : null;
        this.endTime = active ? endTime : null;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Barber getBarber() {
        return barber;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
