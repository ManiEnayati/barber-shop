package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Private blocked interval for the authenticated Barber.")
public record BarberBlockedTimeCreateRequest(
        @Schema(description = "Blocked date in ISO format.", example = "2026-10-20", type = "string", format = "date")
        @NotNull LocalDate date,
        @Schema(description = "Inclusive interval start time.", example = "14:30", type = "string", format = "time")
        @NotNull LocalTime startTime,
        @Schema(description = "Exclusive interval end time.", example = "15:30", type = "string", format = "time")
        @NotNull LocalTime endTime,
        @Schema(description = "Optional private reason visible only through secured Barber APIs.", example = "Equipment maintenance", nullable = true)
        String reason
) {
}
