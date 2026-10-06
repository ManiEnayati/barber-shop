package com.example.barbershop.dto;

import java.time.LocalTime;

public record PublicBarberResponse(
        Long id,
        String name,
        LocalTime workStartTime,
        LocalTime workEndTime
) {
    public static PublicBarberResponse from(BarberResponse barber) {
        return new PublicBarberResponse(
                barber.id(),
                barber.name(),
                barber.workStartTime(),
                barber.workEndTime()
        );
    }
}
