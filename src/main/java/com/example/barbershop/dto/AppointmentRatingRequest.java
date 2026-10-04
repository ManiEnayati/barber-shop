package com.example.barbershop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AppointmentRatingRequest(
        @NotNull @Min(1) @Max(5) Integer rating
) {
}
