package com.example.barbershop.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(
        description = "Customer self-booking choices. Customer identity, booking source, lifecycle status, confirmation status, and acceptance are server controlled; unsupported fields are rejected.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
public class CustomerAppointmentBookingRequest {

    @NotNull
    @JsonProperty
    @Schema(description = "Barber to book.", example = "1")
    private Long barberId;

    @NotNull
    @JsonProperty
    @Schema(description = "Service owned by the selected Barber.", example = "2")
    private Long serviceId;

    @NotNull
    @JsonProperty
    @Schema(description = "Appointment date in ISO format.", example = "2026-10-20", type = "string", format = "date")
    private LocalDate date;

    @NotNull
    @JsonProperty
    @Schema(description = "Concrete appointment start time.", example = "14:30", type = "string", format = "time")
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
