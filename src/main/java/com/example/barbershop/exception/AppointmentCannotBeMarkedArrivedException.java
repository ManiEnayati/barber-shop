package com.example.barbershop.exception;

public class AppointmentCannotBeMarkedArrivedException extends RuntimeException {

    public AppointmentCannotBeMarkedArrivedException() {
        super("Appointment cannot be marked as arrived");
    }
}
