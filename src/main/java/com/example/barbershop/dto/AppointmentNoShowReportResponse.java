package com.example.barbershop.dto;

import com.example.barbershop.entity.AppointmentNoShowReviewStatus;

import java.time.Instant;

public record AppointmentNoShowReportResponse(
        Long id,
        Long appointmentId,
        Long reportingBarberId,
        AppointmentNoShowReviewStatus status,
        Instant reportedAt,
        Instant respondedAt
) {
}
