package com.example.barbershop.dto;

import jakarta.validation.constraints.NotBlank;

public record AppointmentConfirmRequest(@NotBlank String code) {
}
