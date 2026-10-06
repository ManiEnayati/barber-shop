package com.example.barbershop.controller;

import com.example.barbershop.config.OpenApiConfig;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.BarberCalendarResponse;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.BarberDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me/barber")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME)
public class BarberDashboardController {

    private final BarberDashboardService barberDashboardService;

    public BarberDashboardController(BarberDashboardService barberDashboardService) {
        this.barberDashboardService = barberDashboardService;
    }

    @GetMapping
    @Operation(
            operationId = "getMyBarberProfile",
            summary = "Get my Barber profile",
            description = "Returns the Barber profile linked to the authenticated BARBER session. This private representation may include the account phone.",
            tags = "Barber Profile"
    )
    @ApiResponse(responseCode = "200", description = "Authenticated Barber profile returned.")
    public BarberResponse getCurrentBarber(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return barberDashboardService.getCurrentBarber(authenticatedUser.userId());
    }

    @GetMapping("/calendar")
    @Operation(
            operationId = "getBarberCalendar",
            summary = "Get my daily Barber calendar",
            description = "Returns the authenticated Barber's working interval and AVAILABLE, BOOKED, or BLOCKED calendar slots for one date.",
            tags = "Barber Appointments"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Daily calendar returned."),
            @ApiResponse(responseCode = "400", description = "Date query parameter is missing or malformed.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.HttpErrorResponse.class)))
    })
    public BarberCalendarResponse getCalendar(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Parameter(description = "Calendar date in ISO format.", example = "2026-10-20") @RequestParam LocalDate date
    ) {
        return barberDashboardService.getCalendar(authenticatedUser.userId(), date);
    }

    @GetMapping("/appointments")
    @Operation(
            operationId = "listBarberAppointments",
            summary = "List my Barber appointments",
            description = "Lists only appointments owned by the authenticated Barber, optionally filtered by scheduled date and lifecycle status.",
            tags = "Barber Appointments"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owned Barber appointment list returned."),
            @ApiResponse(responseCode = "400", description = "Optional date or status query parameter is malformed.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.HttpErrorResponse.class)))
    })
    public List<AppointmentResponse> getAppointments(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Parameter(description = "Optional scheduled date in ISO format.", example = "2026-10-20") @RequestParam(required = false) LocalDate date,
            @Parameter(description = "Optional appointment lifecycle status.", example = "BOOKED") @RequestParam(required = false) AppointmentStatus status
    ) {
        return barberDashboardService.getAppointments(
                authenticatedUser.userId(), date, status
        );
    }
}
