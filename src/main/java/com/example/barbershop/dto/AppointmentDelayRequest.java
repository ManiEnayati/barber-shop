package com.example.barbershop.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AppointmentDelayRequest(
        @NotNull @Positive Integer delayMinutes
) {
}
