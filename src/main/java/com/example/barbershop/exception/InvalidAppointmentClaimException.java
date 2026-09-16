package com.example.barbershop.exception;

public class InvalidAppointmentClaimException extends RuntimeException {

    public InvalidAppointmentClaimException(String message) {
        super(message);
    }
}
