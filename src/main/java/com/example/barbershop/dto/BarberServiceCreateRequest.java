package com.example.barbershop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BarberServiceCreateRequest(
        @NotNull Long barberId,
        @NotBlank String name,
        @NotNull Integer durationMinutes,
        @NotNull Long price
) {
}
