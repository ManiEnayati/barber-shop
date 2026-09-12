package com.example.barbershop.entity;

import com.example.barbershop.exception.AppointmentCannotBeCancelledException;
import com.example.barbershop.exception.AppointmentCannotBeRescheduledException;
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
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    protected Appointment() {
    }

    public Appointment(
            Barber barber,
            BarberServiceOffering serviceOffering,
            Customer customer,
            LocalDate date,
            LocalTime time
    ) {
        this.barber = barber;
        this.serviceOffering = serviceOffering;
        this.customer = customer;
        this.date = date;
        this.time = time;
        this.status = AppointmentStatus.BOOKED;
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

    public AppointmentStatus getStatus() {
        return status;
    }

    public void cancel() {
        if (status != AppointmentStatus.BOOKED
                && status != AppointmentStatus.CANCELLED) {
            throw new AppointmentCannotBeCancelledException();
        }
        status = AppointmentStatus.CANCELLED;
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
    }
}
