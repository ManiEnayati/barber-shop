package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Customer profile linked to the authenticated account.")
public record CustomerResponse(
        @Schema(description = "Customer profile ID.", example = "15") Long id,
        @Schema(description = "Customer display name.", example = "Sara Ahmadi") String name,
        @Schema(description = "Verified normalized account phone.", example = "+989123456789") String phone
) {
}
