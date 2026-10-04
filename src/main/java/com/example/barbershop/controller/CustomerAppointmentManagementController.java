package com.example.barbershop.controller;

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
    public List<AppointmentResponse> findOwnAppointments(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.findOwnAppointments(user.userId());
    }

    @PatchMapping("/{appointmentId}/reschedule")
    public AppointmentResponse reschedule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long appointmentId,
            @Valid @RequestBody CustomerAppointmentRescheduleRequest request
    ) {
        return managementService.reschedule(user.userId(), appointmentId, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CustomerAppointmentBookingRequest request
    ) {
        return managementService.book(user.userId(), request);
    }

    @PatchMapping("/{appointmentId}/cancel")
    public AppointmentResponse cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long appointmentId
    ) {
        return managementService.cancel(user.userId(), appointmentId);
    }

    @PostMapping("/{appointmentId}/reject")
    public AppointmentResponse rejectBooking(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long appointmentId
    ) {
        return managementService.rejectBooking(user.userId(), appointmentId);
    }

    @PostMapping("/{appointmentId}/no-show-response")
    public AppointmentNoShowReportResponse respondToNoShow(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long appointmentId,
            @Valid @RequestBody NoShowResponseRequest request
    ) {
        return managementService.respondToNoShow(
                user.userId(), appointmentId, request.response());
    }

    @PostMapping("/{appointmentId}/rating")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentRatingResponse rate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentRatingRequest request
    ) {
        return ratingService.rateByCustomer(
                user.userId(), appointmentId, request.rating());
    }
}
