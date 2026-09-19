package com.example.barbershop.dto;

import com.example.barbershop.entity.BarberApplicationStatus;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record AdminBarberApplicationResponse(
        Long id,
        Long userId,
        String phone,
        String name,
        LocalTime workStartTime,
        LocalTime workEndTime,
        BarberApplicationStatus status,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        String reviewNote
) {
}
