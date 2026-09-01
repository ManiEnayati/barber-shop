package com.example.barbershop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentCreateRequest(

        @NotNull
        Long barberId,

        @NotNull
        LocalDate date,

        @NotNull
        LocalTime time,

        @NotBlank
        String clientName
) {
}