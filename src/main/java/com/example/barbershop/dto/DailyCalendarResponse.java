package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Private daily Barber calendar aggregate.")
public record DailyCalendarResponse(
        @Schema(description = "Barber ID.", example = "1") Long barberId,
        @Schema(description = "Barber display name.", example = "Navid Barber") String barberName,
        @Schema(description = "Calendar date.", example = "2026-10-20", type = "string", format = "date") LocalDate date,
        @Schema(description = "Working interval start.", example = "09:00", type = "string", format = "time") LocalTime workStartTime,
        @Schema(description = "Working interval end.", example = "18:00", type = "string", format = "time") LocalTime workEndTime,
        @Schema(description = "Owned appointments on the date.") List<AppointmentResponse> appointments,
        @Schema(description = "Owned private blocked intervals on the date.") List<BlockedTimeResponse> blockedTimes
) {
}
