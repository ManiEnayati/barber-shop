package com.example.barbershop.exception;

public class AppointmentConfirmationAttemptsExceededException
        extends InvalidAppointmentConfirmationException {

    public AppointmentConfirmationAttemptsExceededException() {
        super("Confirmation code attempt limit has been reached");
    }
}
