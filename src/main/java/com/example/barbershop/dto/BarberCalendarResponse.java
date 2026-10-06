package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Authenticated Barber's calendar for one date.")
public record BarberCalendarResponse(
        @Schema(description = "Calendar date.", example = "2026-10-20", type = "string", format = "date") LocalDate date,
        @Schema(description = "Working interval start, or null for a closed date.", example = "09:00", type = "string", format = "time", nullable = true) LocalTime workingStart,
        @Schema(description = "Working interval end, or null for a closed date.", example = "18:00", type = "string", format = "time", nullable = true) LocalTime workingEnd,
        @Schema(description = "Calendar slots in time order.") List<BarberCalendarSlotResponse> slots
) {
}
