package com.example.barbershop.dto;

import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.CancellationReason;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AppointmentResponse(
        Long id,
        Long barberId,
        String barberName,
        Long serviceId,
        String serviceName,
        int durationMinutes,
        LocalDate date,
        LocalTime time,
        LocalTime endTime,
        Long customerId,
        String customerName,
        String customerPhone,
        String guestName,
        String guestPhone,
        AppointmentStatus status,
        CancellationReason cancellationReason,
        String cancellationNote,
        BookingConfirmationStatus confirmationStatus,
        Integer delayMinutes,
        LocalDateTime expectedArrivalTime
) {
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
