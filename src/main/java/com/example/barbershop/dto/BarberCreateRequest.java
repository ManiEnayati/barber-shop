package com.example.barbershop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record BarberCreateRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @NotNull LocalTime workStartTime,
        @NotNull LocalTime workEndTime
) {
}
