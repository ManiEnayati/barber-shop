package com.example.barbershop.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record BlockedTimeResponse(
        Long id,
        Long barberId,
        String barberName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String reason,
        Instant createdAt
) {
    public BlockedTimeResponse(Long id, Long barberId, String barberName,
                               LocalDate date, LocalTime startTime, LocalTime endTime,
                               String reason) {
        this(id, barberId, barberName, date, startTime, endTime, reason, null);
    }
}
