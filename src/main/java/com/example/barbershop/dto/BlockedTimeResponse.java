package com.example.barbershop.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record BlockedTimeResponse(
        Long id,
        Long barberId,
        String barberName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String reason
) {
}
