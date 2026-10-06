package com.example.barbershop.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(
        description = "Guest-only manual booking choices. Barber/customer IDs, booking source, lifecycle state, confirmation state, and acceptance are server controlled; unsupported fields are rejected.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
)
public class BarberAppointmentBookingRequest {

    @NotNull
    @JsonProperty
    @Schema(description = "Service owned by the authenticated Barber.", example = "2")
    private Long serviceId;

    @NotBlank
    @JsonProperty
    @Schema(description = "Guest display name.", example = "Ali Rezaei")
    private String guestName;

    @JsonProperty
    @Schema(description = "Optional Iranian mobile phone; normalized when supplied and never used for automatic Customer linking.", example = "09123456789", nullable = true)
    private String guestPhone;

    @NotNull
    @JsonProperty
    @Schema(description = "Appointment date in ISO format.", example = "2026-10-20", type = "string", format = "date")
    private LocalDate date;

    @NotNull
    @JsonProperty
    @Schema(description = "Concrete appointment start time.", example = "14:30", type = "string", format = "time")
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
