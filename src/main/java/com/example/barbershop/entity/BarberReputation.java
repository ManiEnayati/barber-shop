package com.example.barbershop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "barber_reputations")
public class BarberReputation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "barber_id", nullable = false, unique = true)
    private Barber barber;

    @Column(nullable = false)
    private int score = 100;

    @Column(nullable = false)
    private int completedCount;

    @Column(nullable = false)
    private int delayCount;

    @Column(nullable = false)
    private int cancellationCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected BarberReputation() {
    }

    public BarberReputation(Barber barber, Instant createdAt) {
        if (barber == null || createdAt == null) {
            throw new IllegalArgumentException("Barber and creation time are required");
        }
        this.barber = barber;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Barber getBarber() {
        return barber;
    }

    public int getScore() {
        return score;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public int getDelayCount() {
        return delayCount;
    }

    public int getCancellationCount() {
        return cancellationCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int recordCompleted(Instant occurredAt) {
        completedCount++;
        return changeScore(1, occurredAt);
    }

    public int recordDelay(int penalty, Instant occurredAt) {
        delayCount++;
        return changeScore(-penalty, occurredAt);
    }

    public int recordCancellation(int penalty, Instant occurredAt) {
        cancellationCount++;
        return changeScore(-penalty, occurredAt);
    }

    private int changeScore(int requestedDelta, Instant occurredAt) {
        int oldScore = score;
        score = Math.max(0, Math.min(100, score + requestedDelta));
        updatedAt = occurredAt;
        return score - oldScore;
    }
}
