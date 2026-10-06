package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Business or authorization error returned by the API exception handler.")
public record ApiErrorResponse(
        @Schema(description = "Human-readable error message.", example = "Appointment cannot be rescheduled")
        String message
) {
}
