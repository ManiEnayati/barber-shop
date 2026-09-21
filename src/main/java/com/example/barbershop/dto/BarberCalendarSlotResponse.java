package com.example.barbershop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BarberCalendarSlotResponse(
        LocalTime time,
        BarberCalendarSlotStatus status,
        Long appointmentId
) {
}
