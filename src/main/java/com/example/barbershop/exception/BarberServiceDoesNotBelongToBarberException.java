package com.example.barbershop.exception;

public class BarberServiceDoesNotBelongToBarberException extends RuntimeException {

    public BarberServiceDoesNotBelongToBarberException() {
        super("Barber service does not belong to the selected barber");
    }
}
