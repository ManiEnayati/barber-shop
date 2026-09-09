package com.example.barbershop.dto;

import java.time.LocalTime;

public record AvailableTimeResponse(
        LocalTime startTime,
        LocalTime endTime
) {
}
