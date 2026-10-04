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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "appointment_no_show_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_no_show_report_appointment",
                columnNames = "appointment_id"
        )
)
public class AppointmentNoShowReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true,
            updatable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporting_barber_id", nullable = false, updatable = false)
    private Barber reportingBarber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentNoShowReviewStatus status;

    @Column(nullable = false, updatable = false)
    private Instant reportedAt;

    private Instant respondedAt;

    protected AppointmentNoShowReport() {
    }

    public AppointmentNoShowReport(
            Appointment appointment,
            Barber reportingBarber,
            AppointmentNoShowReviewStatus status,
            Instant reportedAt
    ) {
        this.appointment = appointment;
        this.reportingBarber = reportingBarber;
        this.status = status;
        this.reportedAt = reportedAt;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public Barber getReportingBarber() {
        return reportingBarber;
    }

    public AppointmentNoShowReviewStatus getStatus() {
        return status;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public boolean isPendingCustomerResponse() {
        return status == AppointmentNoShowReviewStatus.PENDING_CUSTOMER;
    }

    public void confirmAbsence(Instant responseTime) {
        requirePending();
        status = AppointmentNoShowReviewStatus.CUSTOMER_CONFIRMED_ABSENCE;
        respondedAt = responseTime;
    }

    public void dispute(Instant responseTime) {
        requirePending();
        status = AppointmentNoShowReviewStatus.DISPUTED;
        respondedAt = responseTime;
    }

    private void requirePending() {
        if (!isPendingCustomerResponse()) {
            throw new IllegalStateException("No-show response is already final");
        }
    }
}
