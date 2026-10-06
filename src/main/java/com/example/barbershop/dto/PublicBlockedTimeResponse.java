package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Public availability-only blocked interval. Private reason and creation metadata are intentionally absent.")
public record PublicBlockedTimeResponse(
        @Schema(description = "Blocked-time ID.", example = "7") Long id,
        @Schema(description = "Owning Barber ID.", example = "1") Long barberId,
        @Schema(description = "Owning Barber name.", example = "Navid Barber") String barberName,
        @Schema(description = "Blocked date.", example = "2026-10-20", type = "string", format = "date") LocalDate date,
        @Schema(description = "Blocked interval start.", example = "14:30", type = "string", format = "time") LocalTime startTime,
        @Schema(description = "Blocked interval end.", example = "15:30", type = "string", format = "time") LocalTime endTime
) {
    public static PublicBlockedTimeResponse from(BlockedTimeResponse blockedTime) {
        return new PublicBlockedTimeResponse(
                blockedTime.id(),
                blockedTime.barberId(),
                blockedTime.barberName(),
                blockedTime.date(),
                blockedTime.startTime(),
                blockedTime.endTime()
        );
    }
}
