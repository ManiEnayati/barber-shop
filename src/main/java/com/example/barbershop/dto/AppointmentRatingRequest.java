package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Raw post-completion star rating. Ratings do not affect reputation.")
public record AppointmentRatingRequest(
        @Schema(description = "Integer rating from one through five.", example = "5", minimum = "1", maximum = "5")
        @NotNull @Min(1) @Max(5) Integer rating
) {
}
