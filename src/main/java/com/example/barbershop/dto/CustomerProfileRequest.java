package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Editable Customer profile fields. User ID and verified phone are derived from the session.")
public record CustomerProfileRequest(
        @Schema(description = "Customer display name.", example = "Sara Ahmadi")
        @NotBlank String name
) {
}
