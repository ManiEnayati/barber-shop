package com.example.barbershop.controller;

import com.example.barbershop.config.OpenApiConfig;
import com.example.barbershop.dto.BarberWeeklyScheduleRequest;
import com.example.barbershop.dto.BarberWeeklyScheduleResponse;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.BarberScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me/barber/schedule")
@Tag(name = "Barber Schedule")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME)
public class BarberScheduleController {

    private final BarberScheduleService scheduleService;

    public BarberScheduleController(BarberScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    @Operation(
            operationId = "getBarberSchedule",
            summary = "Get my weekly schedule",
            description = "Returns the authenticated Barber's seven-day working schedule."
    )
    @ApiResponse(responseCode = "200", description = "Weekly schedule returned.")
    public List<BarberWeeklyScheduleResponse> get(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return scheduleService.getCurrent(user.userId());
    }

    @PutMapping
    @Operation(
            operationId = "updateBarberSchedule",
            summary = "Replace my weekly schedule",
            description = "Atomically replaces the authenticated Barber's weekly schedule. Active days require valid start/end times and the replacement cannot invalidate active appointments."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Weekly schedule replaced."),
            @ApiResponse(responseCode = "400", description = "Schedule structure, day entries, or intervals are invalid.", content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "409", description = "Replacement conflicts with active appointments.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public List<BarberWeeklyScheduleResponse> replace(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody List<@Valid BarberWeeklyScheduleRequest> request) {
        return scheduleService.replace(user.userId(), request);
    }
}
