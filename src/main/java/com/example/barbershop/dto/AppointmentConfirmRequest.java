package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Historical proposal confirmation credential.")
public record AppointmentConfirmRequest(
        @Schema(description = "Six-digit confirmation code for the historical proposal.", example = "123456")
        @NotBlank String code
) {
}
