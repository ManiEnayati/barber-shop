package com.example.barbershop.exception;

public class BlockedTimeOverlapsActiveAppointmentException extends RuntimeException {

    public BlockedTimeOverlapsActiveAppointmentException() {
        super("Blocked time overlaps an active appointment");
    }
}
