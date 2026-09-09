package com.example.barbershop.exception;

public class BarberServiceOfferingNotFoundException extends RuntimeException {

    public BarberServiceOfferingNotFoundException(Long serviceId) {
        super("Barber service not found with id: " + serviceId);
    }
}
