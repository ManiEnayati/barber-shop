package com.example.barbershop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "appointment_confirmations")
public class AppointmentConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @Column(length = 6)
    private String code;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime confirmedAt;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int failedAttempts;

    protected AppointmentConfirmation() {
    }

    public AppointmentConfirmation(Appointment appointment, String code,
                                   LocalDateTime expiresAt) {
        if (appointment == null || appointment.getCustomer() == null
                || appointment.getConfirmationStatus() != BookingConfirmationStatus.PENDING
                || code == null || code.isBlank() || expiresAt == null) {
            throw new IllegalArgumentException("Valid appointment confirmation is required");
        }
        this.appointment = appointment;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public String getCode() {
        return code;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public boolean matchesCode(String submittedCode) {
        return code != null && confirmedAt == null
                && Objects.equals(code, submittedCode);
    }

    public void recordFailedAttempt() {
        failedAttempts++;
    }

    public boolean hasReachedFailedAttemptLimit(int maximumAttempts) {
        return failedAttempts >= maximumAttempts;
    }

    public void markConfirmed(LocalDateTime now) {
        if (confirmedAt != null || code == null || isExpired(now)) {
            throw new IllegalStateException("Confirmation code cannot be reused");
        }
        confirmedAt = now;
        code = null;
    }

    public void invalidate() {
        code = null;
    }
}
