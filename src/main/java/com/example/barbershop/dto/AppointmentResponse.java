package com.example.barbershop.dto;

import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.BookingSource;
import com.example.barbershop.entity.CancellationReason;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Schema(description = "Appointment representation returned to an authorized participant. Registered-Customer and Guest identity fields are mutually contextual and may be null.")
public record AppointmentResponse(
        @Schema(description = "Stable appointment ID retained across rescheduling.", example = "42") Long id,
        @Schema(description = "Owning Barber ID.", example = "1") Long barberId,
        @Schema(description = "Owning Barber display name.", example = "Navid Barber") String barberName,
        @Schema(description = "Booked service ID.", example = "2") Long serviceId,
        @Schema(description = "Booked service name.", example = "Classic haircut") String serviceName,
        @Schema(description = "Booked service duration in minutes.", example = "45") int durationMinutes,
        @Schema(description = "Current scheduled date.", example = "2026-10-20", type = "string", format = "date") LocalDate date,
        @Schema(description = "Current scheduled start time.", example = "14:30", type = "string", format = "time") LocalTime time,
        @Schema(description = "Calculated scheduled end time.", example = "15:15", type = "string", format = "time") LocalTime endTime,
        @Schema(description = "Linked registered Customer ID, or null for an unclaimed Guest.", example = "15", nullable = true) Long customerId,
        @Schema(description = "Linked registered Customer name, when present.", example = "Sara Ahmadi", nullable = true) String customerName,
        @Schema(description = "Linked Customer phone when this authorized response is allowed to expose it.", example = "09123456789", nullable = true) String customerPhone,
        @Schema(description = "Guest name for a manual Guest booking.", example = "Ali Rezaei", nullable = true) String guestName,
        @Schema(description = "Optional normalized Guest phone for a manual booking.", example = "+989123456789", nullable = true) String guestPhone,
        @Schema(description = "Operational appointment lifecycle status.", example = "BOOKED") AppointmentStatus status,
        @Schema(description = "Durable cancellation classification, populated after cancellation.", example = "CUSTOMER_EARLY", nullable = true) CancellationReason cancellationReason,
        @Schema(description = "Optional cancellation note.", example = "Unexpected shop closure", nullable = true) String cancellationNote,
        @Schema(description = "Booking-confirmation lifecycle. Modern bookings are NOT_REQUIRED; historical proposals may be PENDING/CONFIRMED/REJECTED/EXPIRED.", example = "NOT_REQUIRED") BookingConfirmationStatus confirmationStatus,
        @Schema(description = "Current announced Barber delay in minutes.", example = "20", nullable = true) Integer delayMinutes,
        @Schema(description = "Current expected arrival timestamp after a Barber delay.", example = "2026-10-20T14:50:00", nullable = true) LocalDateTime expectedArrivalTime,
        @Schema(description = "Durable booking provenance. Claiming never rewrites BARBER provenance.", example = "CUSTOMER") BookingSource bookingSource,
        @Schema(description = "Whether genuine Customer acceptance has been established. Claiming a manual Guest booking leaves this false.", example = "true") boolean customerAccepted
) {
    public AppointmentResponse(Long id, Long barberId, String barberName,
                               Long serviceId, String serviceName, int durationMinutes,
                               LocalDate date, LocalTime time, LocalTime endTime,
                               Long customerId, String customerName, String customerPhone,
                               String guestName, String guestPhone, AppointmentStatus status,
                               CancellationReason cancellationReason, String cancellationNote,
                               BookingConfirmationStatus confirmationStatus,
                               Integer delayMinutes, LocalDateTime expectedArrivalTime) {
        this(id, barberId, barberName, serviceId, serviceName, durationMinutes,
                date, time, endTime, customerId, customerName, customerPhone,
                guestName, guestPhone, status, cancellationReason, cancellationNote,
                confirmationStatus, delayMinutes, expectedArrivalTime, null, false);
    }

    public AppointmentResponse(Long id, Long barberId, String barberName,
                               Long serviceId, String serviceName, int durationMinutes,
                               LocalDate date, LocalTime time, LocalTime endTime,
                               Long customerId, String customerName, String customerPhone,
                               String guestName, String guestPhone, AppointmentStatus status,
                               CancellationReason cancellationReason, String cancellationNote,
                               BookingConfirmationStatus confirmationStatus) {
        this(id, barberId, barberName, serviceId, serviceName, durationMinutes,
                date, time, endTime, customerId, customerName, customerPhone,
                guestName, guestPhone, status, cancellationReason, cancellationNote,
                confirmationStatus, null, null);
    }

    public AppointmentResponse(Long id, Long barberId, String barberName,
                               Long serviceId, String serviceName, int durationMinutes,
                               LocalDate date, LocalTime time, LocalTime endTime,
                               Long customerId, String customerName, String customerPhone,
                               String guestName, String guestPhone, AppointmentStatus status,
                               CancellationReason cancellationReason, String cancellationNote) {
        this(id, barberId, barberName, serviceId, serviceName, durationMinutes,
                date, time, endTime, customerId, customerName, customerPhone,
                guestName, guestPhone, status, cancellationReason, cancellationNote,
                BookingConfirmationStatus.CONFIRMED, null, null);
    }

    public AppointmentResponse(Long id, Long barberId, String barberName,
                               Long serviceId, String serviceName, int durationMinutes,
                               LocalDate date, LocalTime time, LocalTime endTime,
                               Long customerId, String customerName, String customerPhone,
                               AppointmentStatus status) {
        this(id, barberId, barberName, serviceId, serviceName, durationMinutes,
                date, time, endTime, customerId, customerName, customerPhone,
                null, null, status, null, null, BookingConfirmationStatus.CONFIRMED);
    }
}
