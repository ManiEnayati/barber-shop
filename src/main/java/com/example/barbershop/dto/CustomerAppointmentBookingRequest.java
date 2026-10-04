package com.example.barbershop.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public class CustomerAppointmentBookingRequest {

    @NotNull
    @JsonProperty
    private Long barberId;

    @NotNull
    @JsonProperty
    private Long serviceId;

    @NotNull
    @JsonProperty
    private LocalDate date;

    @NotNull
    @JsonProperty
    private LocalTime time;

    public CustomerAppointmentBookingRequest() {
    }

    public CustomerAppointmentBookingRequest(
            Long barberId,
            Long serviceId,
            LocalDate date,
            LocalTime time
    ) {
        this.barberId = barberId;
        this.serviceId = serviceId;
        this.date = date;
        this.time = time;
    }

    public Long barberId() {
        return barberId;
    }

    public Long serviceId() {
        return serviceId;
    }

    public LocalDate date() {
        return date;
    }

    public LocalTime time() {
        return time;
    }

    @JsonAnySetter
    public void rejectUntrustedField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported booking field: " + name);
    }
}
