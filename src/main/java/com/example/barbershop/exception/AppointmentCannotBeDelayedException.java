package com.example.barbershop.exception;

public class AppointmentCannotBeDelayedException extends RuntimeException {

    public AppointmentCannotBeDelayedException() {
        super("Appointment delay cannot be changed");
    }
}
