package com.example.barbershop.dto;

import com.example.barbershop.entity.AppointmentNoShowReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Durable no-show report/review state separate from the operational appointment status.")
public record AppointmentNoShowReportResponse(
        @Schema(description = "No-show report ID.", example = "6") Long id,
        @Schema(description = "Reported appointment ID.", example = "42") Long appointmentId,
        @Schema(description = "Authenticated Barber who created the report.", example = "1") Long reportingBarberId,
        @Schema(description = "Review state: pending, Customer-confirmed absence, disputed, or not applicable.", example = "DISPUTED") AppointmentNoShowReviewStatus status,
        @Schema(description = "Report timestamp.", example = "2026-10-20T15:00:00Z") Instant reportedAt,
        @Schema(description = "Final Customer response timestamp, when present.", example = "2026-10-20T16:00:00Z", nullable = true) Instant respondedAt
) {
}
