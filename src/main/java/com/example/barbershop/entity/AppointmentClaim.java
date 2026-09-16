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

import java.time.LocalDateTime;

@Entity
@Table(
        name = "appointment_claims",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_appointment_claim_user",
                columnNames = {"appointment_id", "user_id"}
        )
)
public class AppointmentClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentClaimStatus status;

    @Column(nullable = false)
    private LocalDateTime decidedAt;

    protected AppointmentClaim() {
    }

    public AppointmentClaim(
            Appointment appointment,
            User user,
            AppointmentClaimStatus status
    ) {
        this.appointment = appointment;
        this.user = user;
        this.status = status;
        this.decidedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public User getUser() {
        return user;
    }

    public AppointmentClaimStatus getStatus() {
        return status;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }
}
