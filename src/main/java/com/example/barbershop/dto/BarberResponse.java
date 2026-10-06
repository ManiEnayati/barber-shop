package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = "Private Barber profile returned only to authorized workflows. Unlike PublicBarberResponse, it includes the account phone.")
public record BarberResponse(
        @Schema(description = "Barber ID.", example = "1") Long id,
        @Schema(description = "Barber display name.", example = "Navid Barber") String name,
        @Schema(description = "Private account phone.", example = "+989123456789") String phone,
        @Schema(description = "Legacy/default daily work start time.", example = "09:00", type = "string", format = "time") LocalTime workStartTime,
        @Schema(description = "Legacy/default daily work end time.", example = "18:00", type = "string", format = "time") LocalTime workEndTime
) {
}
