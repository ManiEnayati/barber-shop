package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = "Phone-free Barber representation safe for anonymous discovery.")
public record PublicBarberResponse(
        @Schema(description = "Public Barber ID.", example = "1") Long id,
        @Schema(description = "Public Barber display name.", example = "Navid Barber") String name,
        @Schema(description = "Legacy/default daily work start time.", example = "09:00", type = "string", format = "time") LocalTime workStartTime,
        @Schema(description = "Legacy/default daily work end time.", example = "18:00", type = "string", format = "time") LocalTime workEndTime
) {
    public static PublicBarberResponse from(BarberResponse barber) {
        return new PublicBarberResponse(
                barber.id(),
                barber.name(),
                barber.workStartTime(),
                barber.workEndTime()
        );
    }
}
