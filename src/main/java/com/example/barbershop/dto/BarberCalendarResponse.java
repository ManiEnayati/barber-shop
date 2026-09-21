package com.example.barbershop.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record BarberCalendarResponse(
        LocalDate date,
        LocalTime workingStart,
        LocalTime workingEnd,
        List<BarberCalendarSlotResponse> slots
) {
}
