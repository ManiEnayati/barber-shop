package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Schema(description = "One day in the replacement weekly schedule. Active days require a valid start/end interval; inactive days omit times.")
public record BarberWeeklyScheduleRequest(
        @Schema(description = "Day of week.", example = "MONDAY")
        @NotNull DayOfWeek day,
        @Schema(description = "Work start time for an active day.", example = "09:00", type = "string", format = "time", nullable = true)
        LocalTime startTime,
        @Schema(description = "Work end time for an active day.", example = "18:00", type = "string", format = "time", nullable = true)
        LocalTime endTime,
        @Schema(description = "Whether the Barber works on this day.", example = "true")
        @NotNull Boolean active
) {
}
