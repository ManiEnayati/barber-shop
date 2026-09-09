package com.example.barbershop.exception;

public class InvalidBarberScheduleException extends RuntimeException {

    public InvalidBarberScheduleException() {
        super("Barber work schedule is invalid");
    }
}
