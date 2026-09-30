package com.example.barbershop.entity;

public enum BookingConfirmationStatus {
    NOT_REQUIRED,
    PENDING,
    CONFIRMED,
    EXPIRED,
    REJECTED;

    public boolean isActive() {
        return this != EXPIRED && this != REJECTED;
    }
}
