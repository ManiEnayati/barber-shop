package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Barber service offered for booking.")
public record BarberServiceResponse(
        @Schema(description = "Service ID.", example = "2") Long id,
        @Schema(description = "Owning Barber ID.", example = "1") Long barberId,
        @Schema(description = "Owning Barber name.", example = "Navid Barber") String barberName,
        @Schema(description = "Service name.", example = "Classic haircut") String name,
        @Schema(description = "Duration in whole minutes.", example = "45") int durationMinutes,
        @Schema(description = "Price in the application's integer currency unit.", example = "500000") long price
) {
}
