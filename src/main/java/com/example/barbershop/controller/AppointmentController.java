package com.example.barbershop.controller;

import com.example.barbershop.dto.AppointmentConfirmRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Legacy Compatibility")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping("/{appointmentId}/confirm")
    @Operation(
            operationId = "confirmLegacyAppointment",
            summary = "Confirm a historical pending proposal",
            description = "Deprecated compatibility flow for historical PENDING proposal records. It requires the confirmation code, permits at most five failed guesses, and cannot confirm modern Customer self-bookings or BARBER manual Guest bookings.",
            deprecated = true
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historical proposal confirmed."),
            @ApiResponse(responseCode = "400", description = "Code or confirmation record is invalid/expired, or the appointment is not an active PENDING legacy proposal.",
                    content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "The confirmation record reached its failed-attempt limit.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse confirm(
                                       @Parameter(description = "Historical appointment ID.", example = "42") @PathVariable Long appointmentId,
                                       @Valid @RequestBody AppointmentConfirmRequest request) {
        return appointmentService.confirm(appointmentId, request);
    }

}
