package com.example.barbershop.dto;

import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.CancellationReason;

import java.time.LocalDate;
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
        String cancellationNote
) {
    public AppointmentResponse(Long id, Long barberId, String barberName,
                               Long serviceId, String serviceName, int durationMinutes,
                               LocalDate date, LocalTime time, LocalTime endTime,
                               Long customerId, String customerName, String customerPhone,
                               AppointmentStatus status) {
        this(id, barberId, barberName, serviceId, serviceName, durationMinutes,
                date, time, endTime, customerId, customerName, customerPhone,
                null, null, status, null, null);
    }
}
