package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Private blocked-time representation returned to the owning Barber.")
public record BlockedTimeResponse(
        @Schema(description = "Blocked-time ID.", example = "7") Long id,
        @Schema(description = "Owning Barber ID.", example = "1") Long barberId,
        @Schema(description = "Owning Barber name.", example = "Navid Barber") String barberName,
        @Schema(description = "Blocked date.", example = "2026-10-20", type = "string", format = "date") LocalDate date,
        @Schema(description = "Blocked interval start.", example = "14:30", type = "string", format = "time") LocalTime startTime,
        @Schema(description = "Blocked interval end.", example = "15:30", type = "string", format = "time") LocalTime endTime,
        @Schema(description = "Optional private reason.", example = "Equipment maintenance", nullable = true) String reason,
        @Schema(description = "Creation timestamp.", example = "2026-10-10T09:00:00Z", nullable = true) Instant createdAt
) {
    public BlockedTimeResponse(Long id, Long barberId, String barberName,
                               LocalDate date, LocalTime startTime, LocalTime endTime,
                               String reason) {
        this(id, barberId, barberName, date, startTime, endTime, reason, null);
    }
}
