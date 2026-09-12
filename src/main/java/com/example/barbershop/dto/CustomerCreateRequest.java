package com.example.barbershop.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerCreateRequest(
        @NotBlank String name,
        @NotBlank String phone
) {
}
