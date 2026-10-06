package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "OTP verification data. Successful verification creates the HTTP session.")
public record OtpVerifyRequest(
        @Schema(description = "Same phone identity used to request the OTP.", example = "09123456789")
        @NotBlank String phone,
        @Schema(description = "Six-digit one-time code delivered out of band.", example = "123456", pattern = "[0-9]{6}")
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
}
