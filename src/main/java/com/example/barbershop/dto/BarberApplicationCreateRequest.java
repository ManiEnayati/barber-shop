package com.example.barbershop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record BarberApplicationCreateRequest(
        @NotBlank String name,
        @NotNull LocalTime workStartTime,
        @NotNull LocalTime workEndTime
) {
}
