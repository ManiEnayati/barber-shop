package com.example.barbershop.exception;

public class BarberScheduleConflictsWithAppointmentsException extends RuntimeException {

    public BarberScheduleConflictsWithAppointmentsException() {
        super("Weekly schedule conflicts with future appointments");
    }
}
