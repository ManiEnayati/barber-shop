package com.example.barbershop.dto;

import com.example.barbershop.entity.NoShowCustomerResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Final Customer response to a pending Barber no-show report.")
public record NoShowResponseRequest(
        @Schema(description = "CONFIRM_ABSENCE applies the Customer consequence once; DISPUTE records no Customer penalty.", example = "DISPUTE")
        @NotNull NoShowCustomerResponse response
) {
}
