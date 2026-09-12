package com.example.barbershop.exception;

public class AppointmentOverlapsBlockedTimeException extends RuntimeException {

    public AppointmentOverlapsBlockedTimeException() {
        super("Appointment overlaps blocked time");
    }
}
