package com.example.barbershop.exception;

public class InvalidAppointmentConfirmationException extends RuntimeException {

    public InvalidAppointmentConfirmationException(String message) {
        super(message);
    }
}
