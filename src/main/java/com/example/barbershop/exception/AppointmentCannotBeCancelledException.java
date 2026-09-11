package com.example.barbershop.exception;

public class AppointmentCannotBeCancelledException extends RuntimeException {

    public AppointmentCannotBeCancelledException() {
        super("Appointment cannot be cancelled");
    }
}
