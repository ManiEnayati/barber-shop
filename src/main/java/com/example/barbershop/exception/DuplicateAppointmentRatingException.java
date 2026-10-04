package com.example.barbershop.exception;

public class DuplicateAppointmentRatingException extends RuntimeException {

    public DuplicateAppointmentRatingException() {
        super("This appointment has already been rated by this side");
    }
}
