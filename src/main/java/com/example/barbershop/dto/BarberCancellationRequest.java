package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Barber-provided cancellation note. The durable cancellation classification is assigned by the server.")
public record BarberCancellationRequest(
        @Schema(description = "Human-readable reason, up to 255 characters.", example = "Unexpected shop closure", maxLength = 255)
        @NotBlank @Size(max = 255) String reason
) {
}
