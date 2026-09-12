package com.example.barbershop.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentCreateRequest(

        @NotNull
        Long barberId,

        @NotNull
        Long serviceId,

        @NotNull
        Long customerId,

        @NotNull
        LocalDate date,

        @NotNull
        LocalTime time
) {
}
