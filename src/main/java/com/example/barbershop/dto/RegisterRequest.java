package com.example.barbershop.dto;

import com.example.barbershop.entity.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @NotBlank String email,
        @NotBlank String password,
        @NotNull UserRole role
) {
}
