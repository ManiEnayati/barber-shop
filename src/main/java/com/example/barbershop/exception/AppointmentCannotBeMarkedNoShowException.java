package com.example.barbershop.exception;

public class AppointmentCannotBeMarkedNoShowException extends RuntimeException {

    public AppointmentCannotBeMarkedNoShowException() {
        super("Appointment cannot be marked as no-show");
    }
}
