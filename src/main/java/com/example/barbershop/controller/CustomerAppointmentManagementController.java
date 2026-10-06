package com.example.barbershop.controller;

import com.example.barbershop.config.OpenApiConfig;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.AppointmentRatingRequest;
import com.example.barbershop.dto.AppointmentRatingResponse;
import com.example.barbershop.dto.CustomerAppointmentRescheduleRequest;
import com.example.barbershop.dto.CustomerAppointmentBookingRequest;
import com.example.barbershop.dto.AppointmentNoShowReportResponse;
import com.example.barbershop.dto.NoShowResponseRequest;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.CustomerAppointmentManagementService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me/appointments")
@Tag(name = "Customer Appointments")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME)
public class CustomerAppointmentManagementController {

    private final CustomerAppointmentManagementService managementService;
    private final AppointmentRatingService ratingService;

    public CustomerAppointmentManagementController(
            CustomerAppointmentManagementService managementService,
            AppointmentRatingService ratingService
    ) {
        this.managementService = managementService;
        this.ratingService = ratingService;
    }

    @GetMapping
    @Operation(
            operationId = "listMyAppointments",
            summary = "List my appointments",
            description = "Returns active and historical appointments linked to the authenticated Customer, including self-bookings and explicitly claimed Guest appointments. Unclaimed Guest appointments are excluded even when phone numbers match. Results are newest scheduled date/time first."
    )
    @ApiResponse(responseCode = "200", description = "Owned appointment list returned; may be empty.")
    public List<AppointmentResponse> findOwnAppointments(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.findOwnAppointments(user.userId());
    }

    @PatchMapping("/{appointmentId}/reschedule")
    @Operation(
            operationId = "rescheduleCustomerAppointment",
            summary = "Reschedule my appointment",
            description = "Changes only the date/time of an owned active BOOKED appointment. Normal rescheduling uses the one-use opportunity and cutoff; an available Barber-delay remedy may waive the cutoff. Existing Barber, service, identity, and confirmation state are preserved."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment rescheduled using the same appointment ID."),
            @ApiResponse(responseCode = "400", description = "Appointment state, cutoff, opportunity, or requested time is invalid.",
                    content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Requested slot overlaps an appointment or blocked time.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse reschedule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Owned appointment ID.", example = "42") @PathVariable Long appointmentId,
            @Valid @RequestBody CustomerAppointmentRescheduleRequest request
    ) {
        return managementService.reschedule(user.userId(), appointmentId, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createCustomerAppointment",
            summary = "Book an appointment for myself",
            description = "Creates an immediately active CUSTOMER-origin booking for the authenticated verified Customer. Customer identity, BookingSource.CUSTOMER, BOOKED status, and customerAccepted=true are server controlled; do not send customerId, userId, bookingSource, status, or acceptance fields."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Customer appointment created."),
            @ApiResponse(responseCode = "400", description = "Request fields, service ownership, schedule, or appointment time are invalid.",
                    content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "404", description = "Barber or service was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Requested slot overlaps an active appointment or blocked time.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse book(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CustomerAppointmentBookingRequest request
    ) {
        return managementService.book(user.userId(), request);
    }

    @PatchMapping("/{appointmentId}/cancel")
    @Operation(
            operationId = "cancelCustomerAppointment",
            summary = "Cancel my appointment",
            description = "Cancels an owned active BOOKED appointment and assigns the server-derived CUSTOMER_EARLY, CUSTOMER_LATE, or BARBER_DELAY cancellation classification."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment cancelled and classified."),
            @ApiResponse(responseCode = "400", description = "Appointment is not cancellable from its current state.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Owned appointment ID.", example = "42") @PathVariable Long appointmentId
    ) {
        return managementService.cancel(user.userId(), appointmentId);
    }

    @PostMapping("/{appointmentId}/reject")
    @Operation(
            operationId = "rejectCustomerAppointmentProposal",
            summary = "Reject a historical pending booking proposal",
            description = "Authenticated compatibility flow for an owning Customer to reject an active PENDING historical proposal. Modern Customer and manual Guest bookings are not pending proposals."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending historical proposal rejected."),
            @ApiResponse(responseCode = "400", description = "Appointment is not an eligible active PENDING proposal.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentResponse rejectBooking(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Owned historical proposal ID.", example = "42") @PathVariable Long appointmentId
    ) {
        return managementService.rejectBooking(user.userId(), appointmentId);
    }

    @PostMapping("/{appointmentId}/no-show-response")
    @Operation(
            operationId = "respondToNoShowReport",
            summary = "Respond to my no-show report",
            description = "The owning Customer may CONFIRM_ABSENCE or DISPUTE a pending report. Barber assertion alone, silence, and disputes do not deduct Customer reputation; confirming absence applies the existing consequence once. The final response cannot be changed."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Final Customer response recorded."),
            @ApiResponse(responseCode = "400", description = "Response value or request body is invalid.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.HttpErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment or report was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "No pending response is allowed, or a final response already exists.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentNoShowReportResponse respondToNoShow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Owned appointment ID.", example = "42") @PathVariable Long appointmentId,
            @Valid @RequestBody NoShowResponseRequest request
    ) {
        return managementService.respondToNoShow(
                user.userId(), appointmentId, request.response());
    }

    @PostMapping("/{appointmentId}/rating")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createCustomerRating",
            summary = "Rate the Barber",
            description = "Stores one 1-5 rating from the owning Customer for a COMPLETED appointment with a verified linked Customer and customerAccepted=true. Claimed unaccepted manual bookings are ineligible. Ratings do not affect reputation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Customer rating stored."),
            @ApiResponse(responseCode = "400", description = "Rating value or appointment lifecycle is invalid.",
                    content = @Content(schema = @Schema(oneOf = {com.example.barbershop.dto.ApiErrorResponse.class, com.example.barbershop.dto.HttpErrorResponse.class}))),
            @ApiResponse(responseCode = "404", description = "Appointment was not found.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Customer rating already exists for this appointment.",
                    content = @Content(schema = @Schema(implementation = com.example.barbershop.dto.ApiErrorResponse.class)))
    })
    public AppointmentRatingResponse rate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Owned completed appointment ID.", example = "42") @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentRatingRequest request
    ) {
        return ratingService.rateByCustomer(
                user.userId(), appointmentId, request.rating());
    }
}
