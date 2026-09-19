package com.example.barbershop.exception;

public class BarberApplicationNotFoundException extends RuntimeException {

    public BarberApplicationNotFoundException(Long applicationId) {
        super("Barber application not found with id: " + applicationId);
    }
}
