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
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Table(
        name = "reputation_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reputation_event_appointment_subject_type",
                columnNames = {"appointment_id", "subject_type", "event_type"}
        )
)
public class ReputationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, updatable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, updatable = false)
    private ReputationSubjectType subjectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private ReputationEventType eventType;

    @Column(nullable = false, updatable = false)
    private int delta;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ReputationEvent() {
    }

    public ReputationEvent(
            Appointment appointment,
            ReputationSubjectType subjectType,
            ReputationEventType eventType,
            int delta,
            Instant createdAt
    ) {
        this.appointment = appointment;
        this.subjectType = subjectType;
        this.eventType = eventType;
        this.delta = delta;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public ReputationSubjectType getSubjectType() {
        return subjectType;
    }

    public ReputationEventType getEventType() {
        return eventType;
    }

    public int getDelta() {
        return delta;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
