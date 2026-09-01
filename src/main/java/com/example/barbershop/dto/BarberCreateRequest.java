package com.example.barbershop.dto;

import jakarta.validation.constraints.NotBlank;

public record BarberCreateRequest(
        @NotBlank String name,
        @NotBlank String phone
) {
}
