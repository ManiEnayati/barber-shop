package com.example.barbershop.dto;

import com.example.barbershop.entity.NoShowCustomerResponse;
import jakarta.validation.constraints.NotNull;

public record NoShowResponseRequest(
        @NotNull NoShowCustomerResponse response
) {
}
