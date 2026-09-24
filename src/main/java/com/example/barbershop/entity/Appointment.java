package com.example.barbershop.entity;

import com.example.barbershop.exception.AppointmentCannotBeCancelledException;
import com.example.barbershop.exception.AppointmentCannotBeCompletedException;
import com.example.barbershop.exception.AppointmentCannotBeDelayedException;
import com.example.barbershop.exception.AppointmentCannotBeMarkedArrivedException;
import com.example.barbershop.exception.AppointmentCannotBeMarkedNoShowException;
import com.example.barbershop.exception.AppointmentCannotBeRescheduledException;
import com.example.barbershop.exception.InvalidAppointmentConfirmationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "barber_id", nullable = false)
    private Barber barber;

    @ManyToOne
    @JoinColumn(name = "barber_service_id", nullable = false)
    private BarberServiceOffering serviceOffering;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime time;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private String guestName;

    private String guestPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingConfirmationStatus confirmationStatus;

    @Enumerated(EnumType.STRING)
    private CancellationReason cancellationReason;

    private String cancellationNote;

    private Integer delayMinutes;

    private LocalDateTime expectedArrivalTime;

    protected Appointment() {
    }

    public Appointment(
            Barber barber,
            BarberServiceOffering serviceOffering,
            Customer customer,
            LocalDate date,
            LocalTime time
    ) {
        this(barber, serviceOffering, customer, BookingSource.CUSTOMER, date, time);
    }

    public Appointment(
            Barber barber,
            BarberServiceOffering serviceOffering,
            Customer customer,
            BookingSource source,
            LocalDate date,
            LocalTime time
    ) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer or guest name is required");
        }
        this.barber = barber;
        this.serviceOffering = serviceOffering;
        this.customer = customer;
        this.date = date;
        this.time = time;
        this.status = AppointmentStatus.BOOKED;
        this.confirmationStatus = source == BookingSource.BARBER
                ? BookingConfirmationStatus.PENDING
                : BookingConfirmationStatus.CONFIRMED;
    }

    public Appointment(
            Barber barber,
            BarberServiceOffering serviceOffering,
            String guestName,
            String guestPhone,
            LocalDate date,
            LocalTime time
    ) {
        if (guestName == null || guestName.isBlank()) {
            throw new IllegalArgumentException("Customer or guest name is required");
        }
        this.barber = barber;
        this.serviceOffering = serviceOffering;
        this.guestName = guestName.trim();
        this.guestPhone = guestPhone;
        this.date = date;
        this.time = time;
        this.status = AppointmentStatus.BOOKED;
        this.confirmationStatus = BookingConfirmationStatus.NOT_REQUIRED;
    }

    public Long getId() {
        return id;
    }

    public Barber getBarber() {
        return barber;
    }

    public BarberServiceOffering getServiceOffering() {
        return serviceOffering;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getTime() {
        return time;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getGuestName() {
        return guestName;
    }

    public String getGuestPhone() {
        return guestPhone;
    }

    public CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public String getCancellationNote() {
        return cancellationNote;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public BookingConfirmationStatus getConfirmationStatus() {
        return confirmationStatus;
    }

    public Integer getDelayMinutes() {
        return delayMinutes;
    }

    public LocalDateTime getExpectedArrivalTime() {
        return expectedArrivalTime;
    }

    public void updateDelay(int delayMinutes) {
        requireDelayChangeAllowed();
        if (delayMinutes <= 0) {
            throw new IllegalArgumentException("Delay minutes must be positive");
        }
        this.delayMinutes = delayMinutes;
        this.expectedArrivalTime = scheduledStart().plusMinutes(delayMinutes);
    }

    public void removeDelay() {
        requireDelayChangeAllowed();
        delayMinutes = null;
        expectedArrivalTime = null;
    }

    public LocalDateTime getNoShowDeadline(int graceMinutes) {
        if (graceMinutes < 0) {
            throw new IllegalArgumentException("No-show grace minutes cannot be negative");
        }
        LocalDateTime arrivalTime = expectedArrivalTime == null
                ? scheduledStart() : expectedArrivalTime;
        return arrivalTime.plusMinutes(graceMinutes);
    }

    private void requireDelayChangeAllowed() {
        if (status != AppointmentStatus.BOOKED) {
            throw new AppointmentCannotBeDelayedException();
        }
    }

    private LocalDateTime scheduledStart() {
        return LocalDateTime.of(date, time);
    }

    public void confirmBooking() {
        requirePendingBooking();
        confirmationStatus = BookingConfirmationStatus.CONFIRMED;
    }

    public void rejectBooking() {
        requirePendingBooking();
        confirmationStatus = BookingConfirmationStatus.REJECTED;
    }

    public void expireBooking() {
        requirePendingBooking();
        confirmationStatus = BookingConfirmationStatus.EXPIRED;
    }

    private void requirePendingBooking() {
        if (confirmationStatus != BookingConfirmationStatus.PENDING
                || status != AppointmentStatus.BOOKED) {
            throw new InvalidAppointmentConfirmationException(
                    "Booking confirmation is not pending");
        }
    }

    public void cancel() {
        cancel(null, null);
    }

    public void cancel(CancellationReason reason, String note) {
        if (status != AppointmentStatus.BOOKED
                && status != AppointmentStatus.CANCELLED) {
            throw new AppointmentCannotBeCancelledException();
        }
        if (status == AppointmentStatus.CANCELLED) {
            if (reason != null && reason != cancellationReason) {
                throw new AppointmentCannotBeCancelledException();
            }
            return;
        }
        if (reason == CancellationReason.BARBER_DELAY) {
            throw new AppointmentCannotBeCancelledException();
        }
        cancellationReason = reason;
        cancellationNote = note;
        status = AppointmentStatus.CANCELLED;
    }

    public void arrive() {
        if (status != AppointmentStatus.BOOKED) {
            throw new AppointmentCannotBeMarkedArrivedException();
        }
        status = AppointmentStatus.ARRIVED;
    }

    public void complete() {
        if (status != AppointmentStatus.ARRIVED) {
            throw new AppointmentCannotBeCompletedException();
        }
        status = AppointmentStatus.COMPLETED;
    }

    public void markNoShow() {
        if (status != AppointmentStatus.BOOKED) {
            throw new AppointmentCannotBeMarkedNoShowException();
        }
        status = AppointmentStatus.NO_SHOW;
    }

    public void cancelByBarber(String reason) {
        if (status != AppointmentStatus.BOOKED || reason == null
                || reason.isBlank() || reason.strip().length() > 255) {
            throw new AppointmentCannotBeCancelledException();
        }
        cancel(CancellationReason.BARBER_REQUEST, reason.strip());
    }

    public void requireReschedulable() {
        if (status != AppointmentStatus.BOOKED) {
            throw new AppointmentCannotBeRescheduledException();
        }
    }

    public void reschedule(
            BarberServiceOffering serviceOffering,
            LocalDate date,
            LocalTime time
    ) {
        requireReschedulable();
        this.serviceOffering = serviceOffering;
        this.date = date;
        this.time = time;
        if (delayMinutes != null) {
            expectedArrivalTime = scheduledStart().plusMinutes(delayMinutes);
        }
    }

    public void claimBy(Customer customer) {
        if (this.customer != null || guestPhone == null || customer == null) {
            throw new IllegalStateException("Appointment cannot be claimed");
        }
        this.customer = customer;
        this.guestName = null;
        this.guestPhone = null;
    }
}
