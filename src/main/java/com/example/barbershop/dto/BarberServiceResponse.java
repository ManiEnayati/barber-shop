package com.example.barbershop.dto;

public record BarberServiceResponse(
        Long id,
        Long barberId,
        String barberName,
        String name,
        int durationMinutes,
        long price
) {
}
