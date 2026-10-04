package com.example.barbershop.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public class BarberAppointmentBookingRequest {

    @NotNull
    @JsonProperty
    private Long serviceId;

    @NotBlank
    @JsonProperty
    private String guestName;

    @JsonProperty
    private String guestPhone;

    @NotNull
    @JsonProperty
    private LocalDate date;

    @NotNull
    @JsonProperty
    private LocalTime time;

    public BarberAppointmentBookingRequest() {
    }

    public BarberAppointmentBookingRequest(
            Long serviceId,
            String guestName,
            String guestPhone,
            LocalDate date,
            LocalTime time
    ) {
        this.serviceId = serviceId;
        this.guestName = guestName;
        this.guestPhone = guestPhone;
        this.date = date;
        this.time = time;
    }

    public Long serviceId() {
        return serviceId;
    }

    public String guestName() {
        return guestName;
    }

    public String guestPhone() {
        return guestPhone;
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
