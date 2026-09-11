package com.example.barbershop.exception;

public class AppointmentCannotBeRescheduledException extends RuntimeException {

    public AppointmentCannotBeRescheduledException() {
        super("Appointment cannot be rescheduled");
    }
}
