package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Current positive Barber delay in minutes.")
public record AppointmentDelayRequest(
        @Schema(description = "Announced delay in whole minutes.", example = "20", minimum = "1")
        @NotNull @Positive Integer delayMinutes
) {
}
