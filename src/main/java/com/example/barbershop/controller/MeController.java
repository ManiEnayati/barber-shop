package com.example.barbershop.controller;

import com.example.barbershop.config.OpenApiConfig;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.BarberApplicationCreateRequest;
import com.example.barbershop.dto.BarberApplicationResponse;
import com.example.barbershop.dto.CustomerProfileRequest;
import com.example.barbershop.dto.CustomerResponse;
import com.example.barbershop.dto.UserResponse;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.AppointmentClaimService;
import com.example.barbershop.service.BarberApplicationService;
import com.example.barbershop.service.MeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME)
public class MeController {

    private final MeService meService;
    private final AppointmentClaimService appointmentClaimService;
    private final BarberApplicationService barberApplicationService;

    public MeController(
            MeService meService,
            AppointmentClaimService appointmentClaimService,
            BarberApplicationService barberApplicationService
    ) {
        this.meService = meService;
        this.appointmentClaimService = appointmentClaimService;
        this.barberApplicationService = barberApplicationService;
    }

    @GetMapping
    @Operation(
            operationId = "getCurrentUser",
            summary = "Get the authenticated account",
            description = "Returns only the account represented by the current HTTP session.",
            tags = "Account"
    )
    @ApiResponse(responseCode = "200", description = "Authenticated account returned.")
    public UserResponse getCurrentUser(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return meService.getCurrentUser(authenticatedUser.userId());
    }

    @PutMapping("/customer-profile")
    @Operation(
            operationId = "updateCustomerProfile",
            summary = "Create or update my Customer profile",
            description = "Creates or updates the Customer profile linked to the authenticated verified user. User ID, phone, and roles come from the session and cannot be selected by the request.",
            tags = "Account"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer profile created or updated."),
            @ApiResponse(responseCode = "400", description = "Profile request validation failed.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.HttpErrorResponse.class)))
    })
    public CustomerResponse upsertCustomerProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CustomerProfileRequest request
    ) {
        return meService.upsertCustomerProfile(authenticatedUser.userId(), request);
    }

    @GetMapping("/appointment-claims")
    @Operation(
            operationId = "listAppointmentClaims",
            summary = "List my claimable Guest appointments",
            description = "Lists eligible BARBER-origin Guest appointments matching the authenticated verified identity. A phone match alone never links or claims an appointment.",
            tags = "Customer Claims"
    )
    @ApiResponse(responseCode = "200", description = "Claimable appointment list returned.")
    public List<AppointmentResponse> findAppointmentClaims(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return appointmentClaimService.findClaimableAppointments(
                authenticatedUser.userId()
        );
    }

    @PostMapping("/appointment-claims/{appointmentId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "confirmAppointmentClaim",
            summary = "Claim a Guest appointment",
            description = "Explicitly links an eligible Guest appointment to the authenticated Customer. Claiming preserves BookingSource.BARBER and customerAccepted=false; it does not enable Customer reputation consequences or ratings.",
            tags = "Customer Claims"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Claim recorded."),
            @ApiResponse(responseCode = "400", description = "Appointment is not claimable by this identity.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public void confirmAppointmentClaim(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Parameter(description = "Claimable appointment ID.", example = "42") @PathVariable Long appointmentId
    ) {
        appointmentClaimService.confirm(authenticatedUser.userId(), appointmentId);
    }

    @PostMapping("/appointment-claims/{appointmentId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "rejectAppointmentClaim",
            summary = "Reject a Guest appointment claim",
            description = "Records that the authenticated Customer rejects the matching Guest appointment without changing unrelated appointments.",
            tags = "Customer Claims"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Claim rejected."),
            @ApiResponse(responseCode = "400", description = "Appointment is not rejectable by this identity.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public void rejectAppointmentClaim(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Parameter(description = "Claimable appointment ID.", example = "42") @PathVariable Long appointmentId
    ) {
        appointmentClaimService.reject(authenticatedUser.userId(), appointmentId);
    }

    @PostMapping("/barber-application")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "submitBarberApplication",
            summary = "Submit a Barber application",
            description = "Submits an application for the authenticated Customer account. Submission cannot self-approve or assign BARBER/ADMIN roles.",
            tags = "Barber Profile"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Barber application submitted."),
            @ApiResponse(responseCode = "400", description = "Application request validation failed.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.HttpErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Current application/account state does not permit another submission.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public BarberApplicationResponse submitBarberApplication(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody BarberApplicationCreateRequest request
    ) {
        return barberApplicationService.submit(authenticatedUser.userId(), request);
    }

    @GetMapping("/barber-applications")
    @Operation(
            operationId = "listMyBarberApplications",
            summary = "List my Barber applications",
            description = "Returns only applications submitted by the authenticated user.",
            tags = "Barber Profile"
    )
    @ApiResponse(responseCode = "200", description = "Authenticated user's applications returned.")
    public List<BarberApplicationResponse> findBarberApplications(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return barberApplicationService.findForUser(authenticatedUser.userId());
    }
}
