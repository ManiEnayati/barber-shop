package com.example.barbershop.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

public record BarberWeeklyScheduleResponse(
        DayOfWeek day,
        LocalTime startTime,
        LocalTime endTime,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
