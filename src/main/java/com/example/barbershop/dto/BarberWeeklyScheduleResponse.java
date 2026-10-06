package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

@Schema(description = "Persisted working schedule for one weekday.")
public record BarberWeeklyScheduleResponse(
        @Schema(description = "Day of week.", example = "MONDAY") DayOfWeek day,
        @Schema(description = "Work start for an active day.", example = "09:00", type = "string", format = "time", nullable = true) LocalTime startTime,
        @Schema(description = "Work end for an active day.", example = "18:00", type = "string", format = "time", nullable = true) LocalTime endTime,
        @Schema(description = "Whether this is an active workday.", example = "true") boolean active,
        @Schema(description = "Creation timestamp.", example = "2026-10-10T09:00:00Z") Instant createdAt,
        @Schema(description = "Last-update timestamp.", example = "2026-10-11T09:00:00Z") Instant updatedAt
) {
}
