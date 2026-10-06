package com.example.barbershop.controller;

import com.example.barbershop.dto.AvailableTimeResponse;
import com.example.barbershop.dto.PublicBarberResponse;
import com.example.barbershop.service.AppointmentService;
import com.example.barbershop.service.BarberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/barbers")
@Tag(name = "Public Discovery")
public class BarberController {

    private final BarberService barberService;
    private final AppointmentService appointmentService;

    public BarberController(
            BarberService barberService,
            AppointmentService appointmentService
    ) {
        this.barberService = barberService;
        this.appointmentService = appointmentService;
    }

    @GetMapping
    @Operation(
            operationId = "listPublicBarbers",
            summary = "List public Barbers",
            description = "Returns public Barber discovery fields only. Authentication/login phone numbers are intentionally excluded."
    )
    @ApiResponse(responseCode = "200", description = "Public Barber list returned.")
    public List<PublicBarberResponse> findAll() {
        return barberService.findAll().stream()
                .map(PublicBarberResponse::from)
                .toList();
    }

    @GetMapping("/{barberId}/available-times")
    @Operation(
            operationId = "getAvailableTimes",
            summary = "List available appointment times",
            description = "Returns slots that fit the selected Barber service duration and exclude closed schedule periods, blocked times, and active appointment overlaps."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Available time intervals returned."),
            @ApiResponse(responseCode = "400", description = "Invalid date or the service does not belong to the Barber.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Barber or service was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public List<AvailableTimeResponse> findAvailableTimes(
            @Parameter(description = "Public Barber ID.", example = "1") @PathVariable Long barberId,
            @Parameter(description = "Appointment date in ISO format.", example = "2026-10-20") @RequestParam LocalDate date,
            @Parameter(description = "Service ID owned by the selected Barber.", example = "2") @RequestParam Long serviceId
    ) {
        return appointmentService.findAvailableTimes(barberId, date, serviceId);
    }
}
