package com.example.barbershop.exception;

public class AppointmentSlotAlreadyBookedException extends RuntimeException {

    public AppointmentSlotAlreadyBookedException() {
        super("Appointment slot is already booked");
    }
}