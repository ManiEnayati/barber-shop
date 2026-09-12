package com.example.barbershop.exception;

public class BlockedTimeNotFoundException extends RuntimeException {

    public BlockedTimeNotFoundException(Long id) {
        super("Blocked time not found with id: " + id);
    }
}
