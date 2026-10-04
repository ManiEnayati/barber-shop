package com.example.barbershop.dto;

import com.example.barbershop.entity.RatingRaterType;

import java.time.Instant;

public record AppointmentRatingResponse(
        Long id,
        Long appointmentId,
        RatingRaterType raterType,
        int rating,
        Instant createdAt
) {
}
