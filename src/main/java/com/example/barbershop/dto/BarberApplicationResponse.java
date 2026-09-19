package com.example.barbershop.dto;

import com.example.barbershop.entity.BarberApplicationStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record BarberApplicationResponse(
        Long id,
        String name,
        LocalTime workStartTime,
        LocalTime workEndTime,
        BarberApplicationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String reviewNote
) {
}
