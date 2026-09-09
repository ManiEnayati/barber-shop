package com.example.barbershop.exception;

public class InvalidBarberServiceException extends RuntimeException {

    public InvalidBarberServiceException() {
        super("Barber service is invalid");
    }
}
