package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

@Schema(description = "Barber application submitted by the authenticated Customer account.")
public record BarberApplicationCreateRequest(
        @Schema(description = "Requested public Barber name.", example = "Navid Barber")
        @NotBlank String name,
        @Schema(description = "Requested daily work start time.", example = "09:00", type = "string", format = "time")
        @NotNull LocalTime workStartTime,
        @Schema(description = "Requested daily work end time.", example = "18:00", type = "string", format = "time")
        @NotNull LocalTime workEndTime
) {
}
