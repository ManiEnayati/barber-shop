package com.example.barbershop.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentResponse(
        Long id,
        Long barberId,
        String barberName,
        LocalDate date,
        LocalTime time,
        String clientName
) {
}