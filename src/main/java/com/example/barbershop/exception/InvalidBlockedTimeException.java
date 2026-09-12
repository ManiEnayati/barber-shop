package com.example.barbershop.exception;

public class InvalidBlockedTimeException extends RuntimeException {

    public InvalidBlockedTimeException() {
        super("Blocked time is invalid");
    }
}
