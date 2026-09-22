package com.example.barbershop.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record BarberWeeklyScheduleRequest(
        @NotNull DayOfWeek day,
        LocalTime startTime,
        LocalTime endTime,
        @NotNull Boolean active
) {
}
