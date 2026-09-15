package com.example.barbershop.dto;

import com.example.barbershop.entity.BookingSource;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentCreateRequest(

        @NotNull
        Long barberId,

        @NotNull
        Long serviceId,

        Long customerId,

        String guestName,

        String guestPhone,

        @NotNull
        LocalDate date,

        @NotNull
        LocalTime time,

        BookingSource source
) {
    public AppointmentCreateRequest(Long barberId, Long serviceId, Long customerId,
                                    String guestName, String guestPhone,
                                    LocalDate date, LocalTime time) {
        this(barberId, serviceId, customerId, guestName, guestPhone, date, time, null);
    }

    public AppointmentCreateRequest(Long barberId, Long serviceId, Long customerId,
                                    LocalDate date, LocalTime time) {
        this(barberId, serviceId, customerId, null, null, date, time, null);
    }

    @AssertTrue(message = "Customer or guest name is required")
    public boolean isCustomerOrGuestPresent() {
        return customerId != null || (guestName != null && !guestName.isBlank());
    }
}
