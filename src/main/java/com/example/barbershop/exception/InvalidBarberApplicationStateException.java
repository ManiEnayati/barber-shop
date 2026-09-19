package com.example.barbershop.exception;

public class InvalidBarberApplicationStateException extends RuntimeException {

    public InvalidBarberApplicationStateException(String message) {
        super(message);
    }
}
