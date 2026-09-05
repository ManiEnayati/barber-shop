package com.example.barbershop.exception;

public class BarberNotFoundException extends RuntimeException {

    public BarberNotFoundException(Long barberId) {
        super("Barber not found with id: " + barberId);
    }
}