package com.example.barbershop.exception;

public class InvalidAppointmentRatingException extends RuntimeException {

    public InvalidAppointmentRatingException(String message) {
        super(message);
    }
}
