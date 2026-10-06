package com.example.barbershop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Phone number for OTP delivery. Iranian local and supported international forms are normalized before use.")
public record OtpRequest(
        @Schema(description = "Iranian mobile phone number.", example = "09123456789")
        @NotBlank String phone
) {
}
