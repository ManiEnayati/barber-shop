package com.example.barbershop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
        @NotBlank String phone,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
}
