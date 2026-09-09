package com.example.barbershop.entity;

import jakarta.persistence.*;

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

    @Column(nullable = false)
    private String clientName;

    protected Appointment() {
    }

    public Appointment(
            Barber barber,
            BarberServiceOffering serviceOffering,
            LocalDate date,
            LocalTime time,
            String clientName
    ) {
        this.barber = barber;
        this.serviceOffering = serviceOffering;
        this.date = date;
        this.time = time;
        this.clientName = clientName;
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

    public String getClientName() {
        return clientName;
    }
}
