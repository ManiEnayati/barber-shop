package com.example.barbershop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CheckConstraint;
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

import java.time.Instant;

@Entity
@Table(
        name = "appointment_ratings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_appointment_rating_appointment_rater",
                columnNames = {"appointment_id", "rater_type"}
        ),
        check = @CheckConstraint(
                name = "ck_appointment_rating_value",
                constraint = "rating between 1 and 5"
        )
)
public class AppointmentRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(name = "rater_type", nullable = false)
    private RatingRaterType raterType;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AppointmentRating() {
    }

    public AppointmentRating(
            Appointment appointment,
            RatingRaterType raterType,
            int rating,
            Instant createdAt
    ) {
        if (appointment == null || raterType == null || createdAt == null) {
            throw new IllegalArgumentException("Appointment, rater type and creation time are required");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        this.appointment = appointment;
        this.raterType = raterType;
        this.rating = rating;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public RatingRaterType getRaterType() {
        return raterType;
    }

    public int getRating() {
        return rating;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
