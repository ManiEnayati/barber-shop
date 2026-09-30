package com.example.barbershop.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CustomerAppointmentRescheduleRequest(
        @NotNull LocalDate date,
        @NotNull LocalTime time
) {
}
