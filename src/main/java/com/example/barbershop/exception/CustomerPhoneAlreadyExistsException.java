package com.example.barbershop.exception;

public class CustomerPhoneAlreadyExistsException extends RuntimeException {

    public CustomerPhoneAlreadyExistsException() {
        super("Customer with this phone already exists");
    }
}
