package com.example.barbershop.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record BlockedTimeCreateRequest(
        @NotNull Long barberId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        String reason
) {
}
