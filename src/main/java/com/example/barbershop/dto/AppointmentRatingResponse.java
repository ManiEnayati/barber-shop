package com.example.barbershop.dto;

import com.example.barbershop.entity.RatingRaterType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Stored raw appointment rating. It is independent from reputation scoring.")
public record AppointmentRatingResponse(
        @Schema(description = "Rating record ID.", example = "9") Long id,
        @Schema(description = "Rated appointment ID.", example = "42") Long appointmentId,
        @Schema(description = "Side that submitted this rating.", example = "CUSTOMER") RatingRaterType raterType,
        @Schema(description = "Stored integer rating.", example = "5", minimum = "1", maximum = "5") int rating,
        @Schema(description = "Creation timestamp.", example = "2026-10-20T16:00:00Z") Instant createdAt
) {
}
