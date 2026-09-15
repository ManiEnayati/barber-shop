package com.example.barbershop.exception;

public class ConfirmationCodeExpiredException extends InvalidAppointmentConfirmationException {

    public ConfirmationCodeExpiredException() {
        super("Confirmation code has expired");
    }
}
