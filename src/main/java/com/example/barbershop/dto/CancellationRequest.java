package com.example.barbershop.dto;

import com.example.barbershop.entity.CancellationReason;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record CancellationRequest(@NotNull CancellationReason reason, String note) {

    @AssertTrue(message = "Policy-classified cancellation reasons are reserved")
    public boolean isSupportedReason() {
        return reason != CancellationReason.CUSTOMER_EARLY
                && reason != CancellationReason.CUSTOMER_LATE
                && reason != CancellationReason.BARBER_DELAY;
    }
}
