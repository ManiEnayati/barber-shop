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
@Table(name = "customer_reputations")
public class CustomerReputation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(nullable = false)
    private int score = 100;

    @Column(nullable = false)
    private int completedCount;

    @Column(nullable = false)
    private int lateCancellationCount;

    @Column(nullable = false)
    private int noShowCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CustomerReputation() {
    }

    public CustomerReputation(Customer customer, Instant createdAt) {
        if (customer == null || createdAt == null) {
            throw new IllegalArgumentException("Customer and creation time are required");
        }
        this.customer = customer;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public int getScore() {
        return score;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public int getLateCancellationCount() {
        return lateCancellationCount;
    }

    public int getNoShowCount() {
        return noShowCount;
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

    public int recordLateCancellation(Instant occurredAt) {
        lateCancellationCount++;
        return changeScore(-3, occurredAt);
    }

    public int recordNoShow(Instant occurredAt) {
        noShowCount++;
        return changeScore(-8, occurredAt);
    }

    private int changeScore(int requestedDelta, Instant occurredAt) {
        int oldScore = score;
        score = Math.max(0, Math.min(100, score + requestedDelta));
        updatedAt = occurredAt;
        return score - oldScore;
    }
}
