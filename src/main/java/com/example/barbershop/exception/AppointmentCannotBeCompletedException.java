package com.example.barbershop.exception;

public class AppointmentCannotBeCompletedException extends RuntimeException {

    public AppointmentCannotBeCompletedException() {
        super("Appointment cannot be completed");
    }
}
