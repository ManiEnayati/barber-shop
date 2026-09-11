package com.example.barbershop.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentRescheduleRequest(
        @NotNull Long serviceId,
        @NotNull LocalDate date,
        @NotNull LocalTime time
) {
}
