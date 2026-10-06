package com.example.barbershop.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record PublicBlockedTimeResponse(
        Long id,
        Long barberId,
        String barberName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {
    public static PublicBlockedTimeResponse from(BlockedTimeResponse blockedTime) {
        return new PublicBlockedTimeResponse(
                blockedTime.id(),
                blockedTime.barberId(),
                blockedTime.barberName(),
                blockedTime.date(),
                blockedTime.startTime(),
                blockedTime.endTime()
        );
    }
}
