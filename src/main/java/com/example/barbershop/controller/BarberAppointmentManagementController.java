package com.example.barbershop.controller;

import com.example.barbershop.config.OpenApiConfig;
import com.example.barbershop.dto.AppointmentDelayRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.AppointmentRatingRequest;
import com.example.barbershop.dto.AppointmentRatingResponse;
import com.example.barbershop.dto.BarberCancellationRequest;
import com.example.barbershop.dto.BarberAppointmentBookingRequest;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.BarberAppointmentManagementService;
import com.example.barbershop.service.AppointmentRatingService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/barber/appointments")
@Tag(name = "Barber Appointments")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME)
public class BarberAppointmentManagementController {

    private final BarberAppointmentManagementService managementService;
    private final AppointmentRatingService ratingService;

    public BarberAppointmentManagementController(
            BarberAppointmentManagementService managementService,
            AppointmentRatingService ratingService
    ) {
        this.managementService = managementService;
        this.ratingService = ratingService;
    }

    @PatchMapping("/{appointmentId}/arrive")
    @Operation(
            operationId = "markAppointmentArrived",
            summary = "Mark a Customer arrived",
            description = "Marks an owned BOOKED appointment ARRIVED. Only the authenticated owning Barber may perform the transition."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment marked ARRIVED."),
            @ApiResponse(responseCode = "400", description = "Current lifecycle state does not allow arrival.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse arrive(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Parameter(description = "Owned appointment ID.", example = "42") @PathVariable Long appointmentId) {
        return managementService.arrive(user.userId(), appointmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createBarberGuestAppointment",
            summary = "Create a manual Guest appointment",
            description = "Guest-only manual booking for the authenticated Barber. The selected service must belong to that Barber. The server stores BookingSource.BARBER, customer=null, customerAccepted=false, BOOKED status, and NOT_REQUIRED confirmation. Phone matching never auto-links a Customer; do not send customerId, barberId, bookingSource, status, or acceptance fields."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Manual Guest appointment created."),
            @ApiResponse(responseCode = "400", description = "Request, Guest phone, service ownership, schedule, or time is invalid.", content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "404", description = "Service was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Requested slot overlaps an appointment or blocked time.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse book(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody BarberAppointmentBookingRequest request
    ) {
        return managementService.book(user.userId(), request);
    }

    @PatchMapping("/{appointmentId}/complete")
    @Operation(
            operationId = "completeAppointment",
            summary = "Complete an appointment",
            description = "Marks an owned ARRIVED appointment COMPLETED and finalizes the existing idempotent reputation consequences."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment completed."),
            @ApiResponse(responseCode = "400", description = "Current lifecycle state does not allow completion.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse complete(@AuthenticationPrincipal AuthenticatedUser user,
                                        @Parameter(description = "Owned appointment ID.", example = "42") @PathVariable Long appointmentId) {
        return managementService.complete(user.userId(), appointmentId);
    }

    @PatchMapping("/{appointmentId}/no-show")
    @Operation(
            operationId = "markAppointmentNoShow",
            summary = "Report an appointment no-show",
            description = "After the grace-period deadline, records an operational NO_SHOW and a review report. The Barber's assertion alone does not deduct Customer reputation; an eligible Customer must confirm absence for that consequence."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "NO_SHOW outcome and review report recorded."),
            @ApiResponse(responseCode = "400", description = "Lifecycle state or delay-adjusted no-show deadline does not allow this report.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse markNoShow(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Parameter(description = "Owned appointment ID.", example = "42") @PathVariable Long appointmentId) {
        return managementService.markNoShow(user.userId(), appointmentId);
    }

    @PatchMapping("/{appointmentId}/delay")
    @Operation(
            operationId = "setAppointmentDelay",
            summary = "Create or update a Barber delay",
            description = "Sets a positive current delay for an owned BOOKED appointment and grants/restores the Customer delay remedy. Historical maximum delay is retained for Barber reputation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delay updated."),
            @ApiResponse(responseCode = "400", description = "Delay value or appointment state is invalid.", content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse updateDelay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Owned appointment ID.", example = "42") @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentDelayRequest request
    ) {
        return managementService.updateDelay(
                user.userId(), appointmentId, request.delayMinutes());
    }

    @DeleteMapping("/{appointmentId}/delay")
    @Operation(
            operationId = "removeAppointmentDelay",
            summary = "Remove the current Barber delay",
            description = "Clears current delay fields but preserves the Customer remedy already granted and the historical maximum Barber delay."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current delay fields cleared."),
            @ApiResponse(responseCode = "400", description = "Appointment state does not allow delay removal.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse removeDelay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Owned appointment ID.", example = "42") @PathVariable Long appointmentId
    ) {
        return managementService.removeDelay(user.userId(), appointmentId);
    }

    @PatchMapping("/{appointmentId}/cancel")
    @Operation(
            operationId = "cancelBarberAppointment",
            summary = "Cancel an appointment as Barber",
            description = "Cancels an owned appointment with durable BARBER_REQUEST classification and the existing time-based Barber reputation consequence."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment cancelled by Barber."),
            @ApiResponse(responseCode = "400", description = "Reason or appointment lifecycle state is invalid.", content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Parameter(description = "Owned appointment ID.", example = "42") @PathVariable Long appointmentId,
                                      @Valid @RequestBody BarberCancellationRequest request) {
        return managementService.cancel(user.userId(), appointmentId, request.reason());
    }

    @PostMapping("/{appointmentId}/rating")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createBarberRating",
            summary = "Rate the Customer",
            description = "Stores one 1-5 Barber rating for an owned COMPLETED appointment whose verified linked Customer has customerAccepted=true. Guest and claimed-unaccepted manual bookings are ineligible. Ratings do not affect reputation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Barber rating stored."),
            @ApiResponse(responseCode = "400", description = "Rating value or appointment lifecycle is invalid.", content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Barber rating already exists for this appointment.", content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentRatingResponse rate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Owned completed appointment ID.", example = "42") @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentRatingRequest request
    ) {
        return ratingService.rateByBarber(
                user.userId(), appointmentId, request.rating());
    }
}
