package com.example.barbershop.dto;

import com.example.barbershop.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "Authenticated account identity returned after OTP verification and by GET /api/me.")
public record UserResponse(
        @Schema(description = "Internal account ID for display/reference only; authorization always uses the session.", example = "10") Long id,
        @Schema(description = "Normalized verified login phone.", example = "+989123456789") String phone,
        @Schema(description = "Whether phone verification is complete.", example = "true") boolean phoneVerified,
        @Schema(description = "Roles granted by trusted backend workflows.", example = "[\"CUSTOMER\"]") Set<UserRole> roles
) {
}
