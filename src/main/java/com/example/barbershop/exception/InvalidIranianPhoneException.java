package com.example.barbershop.exception;

public class InvalidIranianPhoneException extends RuntimeException {

    public InvalidIranianPhoneException() {
        super("Invalid Iranian mobile phone number");
    }
}
