package com.example.barbershop.entity;

import com.example.barbershop.exception.InvalidBarberApplicationStateException;
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

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "barber_applications")
public class BarberApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalTime workStartTime;

    @Column(nullable = false)
    private LocalTime workEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BarberApplicationStatus status;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    private String reviewNote;

    protected BarberApplication() {
    }

    public BarberApplication(
            User user,
            String name,
            LocalTime workStartTime,
            LocalTime workEndTime,
            LocalDateTime submittedAt
    ) {
        this.user = user;
        this.name = name;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
        this.status = BarberApplicationStatus.PENDING;
        this.submittedAt = submittedAt;
    }

    public void approve(LocalDateTime reviewedAt) {
        requirePending();
        status = BarberApplicationStatus.APPROVED;
        this.reviewedAt = reviewedAt;
        reviewNote = null;
    }

    public void reject(LocalDateTime reviewedAt, String reviewNote) {
        requirePending();
        status = BarberApplicationStatus.REJECTED;
        this.reviewedAt = reviewedAt;
        this.reviewNote = reviewNote;
    }

    private void requirePending() {
        if (status != BarberApplicationStatus.PENDING) {
            throw new InvalidBarberApplicationStateException(
                    "Only pending barber applications may be reviewed"
            );
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public LocalTime getWorkStartTime() {
        return workStartTime;
    }

    public LocalTime getWorkEndTime() {
        return workEndTime;
    }

    public BarberApplicationStatus getStatus() {
        return status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }
}
