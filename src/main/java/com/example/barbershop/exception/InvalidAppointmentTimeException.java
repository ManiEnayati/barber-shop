package com.example.barbershop.exception;

public class InvalidAppointmentTimeException extends RuntimeException {

    public InvalidAppointmentTimeException() {
        super("Appointment time is outside the allowed schedule");
    }
}