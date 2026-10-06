package com.example.barbershop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "One time point in the Barber's daily operational calendar.")
public record BarberCalendarSlotResponse(
        @Schema(description = "Slot start time.", example = "14:30", type = "string", format = "time") LocalTime time,
        @Schema(description = "Whether the time is AVAILABLE, BOOKED, or BLOCKED.", example = "BOOKED") BarberCalendarSlotStatus status,
        @Schema(description = "Appointment ID for a BOOKED slot; omitted otherwise.", example = "42", nullable = true) Long appointmentId
) {
}
