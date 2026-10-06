package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "ADMIN rejection decision details.")
public record BarberApplicationRejectRequest(
        @Schema(description = "Required reason recorded with the rejection.", example = "Identity documents require correction")
        @NotBlank String note
) {
}
