package com.example.barbershop.entity;

public enum AppointmentStatus {
    BOOKED,
    ARRIVED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public boolean isActive() {
        return this == BOOKED || this == ARRIVED;
    }
}
