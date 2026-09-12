package com.example.barbershop.exception;

public class BlockedTimeOverlapsAnotherBlockedTimeException extends RuntimeException {

    public BlockedTimeOverlapsAnotherBlockedTimeException() {
        super("Blocked time overlaps another blocked time");
    }
}
