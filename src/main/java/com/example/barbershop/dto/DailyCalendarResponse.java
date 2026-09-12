package com.example.barbershop.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record DailyCalendarResponse(
        Long barberId,
        String barberName,
        LocalDate date,
        LocalTime workStartTime,
        LocalTime workEndTime,
        List<AppointmentResponse> appointments,
        List<BlockedTimeResponse> blockedTimes
) {
}
