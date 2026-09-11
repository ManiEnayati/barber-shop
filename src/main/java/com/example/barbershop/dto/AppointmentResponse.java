package com.example.barbershop.dto;

import com.example.barbershop.entity.AppointmentStatus;

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
        String clientName,
        AppointmentStatus status
) {
}
