package com.example.barbershop.dto;

import com.example.barbershop.entity.CancellationReason;

public record AppointmentCancelRequest(CancellationReason reason, String note) {
}
