package com.example.barbershop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BarberCancellationRequest(
        @NotBlank @Size(max = 255) String reason
) {
}
