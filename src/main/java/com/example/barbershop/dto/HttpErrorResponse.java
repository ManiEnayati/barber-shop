package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Framework-generated error body used for malformed requests and validation failures.")
public record HttpErrorResponse(
        @Schema(description = "Time the error was generated.", example = "2026-10-20T10:30:00Z")
        Instant timestamp,
        @Schema(description = "HTTP status code.", example = "400")
        int status,
        @Schema(description = "HTTP error name.", example = "Bad Request")
        String error,
        @Schema(description = "Request path that failed.", example = "/api/me/appointments")
        String path
) {
}
