package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "New concrete date/time for an existing Customer appointment. Barber and service cannot be changed.")
public record CustomerAppointmentRescheduleRequest(
        @Schema(description = "New appointment date in ISO format.", example = "2026-10-20", type = "string", format = "date")
        @NotNull LocalDate date,
        @Schema(description = "New appointment start time.", example = "14:30", type = "string", format = "time")
        @NotNull LocalTime time
) {
}
