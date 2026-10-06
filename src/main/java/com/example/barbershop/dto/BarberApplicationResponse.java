package com.example.barbershop.dto;

import com.example.barbershop.entity.BarberApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Schema(description = "Barber application as visible to its submitting user.")
public record BarberApplicationResponse(
        @Schema(description = "Application ID.", example = "8") Long id,
        @Schema(description = "Requested Barber name.", example = "Navid Barber") String name,
        @Schema(description = "Requested work start.", example = "09:00", type = "string", format = "time") LocalTime workStartTime,
        @Schema(description = "Requested work end.", example = "18:00", type = "string", format = "time") LocalTime workEndTime,
        @Schema(description = "Review lifecycle status.", example = "PENDING") BarberApplicationStatus status,
        @Schema(description = "Submission timestamp.", example = "2026-10-10T09:00:00") LocalDateTime submittedAt,
        @Schema(description = "Review timestamp when decided.", example = "2026-10-11T11:00:00", nullable = true) LocalDateTime reviewedAt,
        @Schema(description = "ADMIN review note when present.", example = "Identity documents require correction", nullable = true) String reviewNote
) {
}
