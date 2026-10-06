package com.example.barbershop.controller;

import com.example.barbershop.config.OpenApiConfig;
import com.example.barbershop.dto.BarberServiceManagementRequest;
import com.example.barbershop.dto.BarberServiceResponse;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.BarberServiceManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/barber/services")
@Tag(name = "Barber Services")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME)
public class BarberServiceManagementController {

    private final BarberServiceManagementService managementService;

    public BarberServiceManagementController(
            BarberServiceManagementService managementService
    ) {
        this.managementService = managementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createBarberService",
            summary = "Create one of my services",
            description = "Creates a service owned by the authenticated Barber. Barber identity is derived from the session; barberId is not accepted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Barber service created."),
            @ApiResponse(responseCode = "400", description = "Name, duration, price, or request shape is invalid.", content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class})))
    })
    public BarberServiceResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody BarberServiceManagementRequest request
    ) {
        return managementService.create(user.userId(), request);
    }

    @PutMapping("/{serviceId}")
    @Operation(
            operationId = "updateBarberService",
            summary = "Update one of my services",
            description = "Updates an existing service only when it belongs to the authenticated Barber."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Barber service updated."),
            @ApiResponse(responseCode = "400", description = "Service fields or request shape is invalid.", content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "404", description = "Service was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public BarberServiceResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Owned service ID.", example = "2") @PathVariable Long serviceId,
            @Valid @RequestBody BarberServiceManagementRequest request
    ) {
        return managementService.update(user.userId(), serviceId, request);
    }

    @DeleteMapping("/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "deleteBarberService",
            summary = "Delete one of my services",
            description = "Deletes an existing service only when it belongs to the authenticated Barber."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Barber service deleted."),
            @ApiResponse(responseCode = "404", description = "Service was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public void delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Owned service ID.", example = "2") @PathVariable Long serviceId
    ) {
        managementService.delete(user.userId(), serviceId);
    }
}
