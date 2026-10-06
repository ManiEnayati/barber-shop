package com.example.barbershop.controller;

import com.example.barbershop.config.OpenApiConfig;
import com.example.barbershop.dto.AdminBarberApplicationResponse;
import com.example.barbershop.dto.BarberApplicationRejectRequest;
import com.example.barbershop.entity.BarberApplicationStatus;
import com.example.barbershop.service.BarberApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/barber-applications")
@Tag(name = "Admin - Barber Applications")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME)
public class AdminBarberApplicationController {

    private final BarberApplicationService barberApplicationService;

    public AdminBarberApplicationController(
            BarberApplicationService barberApplicationService
    ) {
        this.barberApplicationService = barberApplicationService;
    }

    @GetMapping
    @Operation(operationId = "listBarberApplicationsAdmin", summary = "List Barber applications", description = "ADMIN-only application review list, optionally filtered by status. The response includes private applicant account data needed for review.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Barber applications returned."),
            @ApiResponse(responseCode = "400", description = "Optional status query parameter is malformed.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.HttpErrorResponse.class)))
    })
    public List<AdminBarberApplicationResponse> findAll(
            @Parameter(description = "Optional application status filter.", example = "PENDING") @RequestParam(required = false) BarberApplicationStatus status
    ) {
        return barberApplicationService.findAll(status);
    }

    @PostMapping("/{applicationId}/approve")
    @Operation(operationId = "approveBarberApplication", summary = "Approve a Barber application", description = "ADMIN-only transition that approves one pending application and grants the linked Barber profile/role exactly once.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application approved."),
            @ApiResponse(responseCode = "404", description = "Application was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Application is not in an approvable state.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AdminBarberApplicationResponse approve(
            @Parameter(description = "Application ID.", example = "8") @PathVariable Long applicationId
    ) {
        return barberApplicationService.approve(applicationId);
    }

    @PostMapping("/{applicationId}/reject")
    @Operation(operationId = "rejectBarberApplication", summary = "Reject a Barber application", description = "ADMIN-only transition that rejects one pending application with a required review note and does not grant the BARBER role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application rejected."),
            @ApiResponse(responseCode = "400", description = "Review note validation failed.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.HttpErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Application is not in a rejectable state.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AdminBarberApplicationResponse reject(
            @Parameter(description = "Application ID.", example = "8") @PathVariable Long applicationId,
            @Valid @RequestBody BarberApplicationRejectRequest request
    ) {
        return barberApplicationService.reject(applicationId, request.note());
    }
}
