package com.example.barbershop.dto;

import com.example.barbershop.entity.CancellationReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record CancellationRequest(@NotNull CancellationReason reason, String note) {

    @AssertTrue(message = "BARBER_DELAY is reserved for the delay system")
    public boolean isSupportedReason() {
        return reason != CancellationReason.BARBER_DELAY;
    }
}
