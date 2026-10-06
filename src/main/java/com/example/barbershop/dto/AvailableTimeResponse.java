package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = "Available appointment interval for the selected service duration.")
public record AvailableTimeResponse(
        @Schema(description = "Available start time.", example = "14:30", type = "string", format = "time") LocalTime startTime,
        @Schema(description = "Calculated service end time.", example = "15:15", type = "string", format = "time") LocalTime endTime
) {
}
