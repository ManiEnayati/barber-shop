package com.example.barbershop.dto;

import java.time.LocalTime;

public record BarberResponse(
        Long id,
        String name,
        String phone,
        LocalTime workStartTime,
        LocalTime workEndTime
) {
}
