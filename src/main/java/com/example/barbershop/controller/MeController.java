package com.example.barbershop.controller;

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
    public UserResponse getCurrentUser(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return meService.getCurrentUser(authenticatedUser.userId());
    }

    @PutMapping("/customer-profile")
    public CustomerResponse upsertCustomerProfile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CustomerProfileRequest request
    ) {
        return meService.upsertCustomerProfile(authenticatedUser.userId(), request);
    }

    @GetMapping("/appointment-claims")
    public List<AppointmentResponse> findAppointmentClaims(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return appointmentClaimService.findClaimableAppointments(
                authenticatedUser.userId()
        );
    }

    @PostMapping("/appointment-claims/{appointmentId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmAppointmentClaim(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long appointmentId
    ) {
        appointmentClaimService.confirm(authenticatedUser.userId(), appointmentId);
    }

    @PostMapping("/appointment-claims/{appointmentId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectAppointmentClaim(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long appointmentId
    ) {
        appointmentClaimService.reject(authenticatedUser.userId(), appointmentId);
    }

    @PostMapping("/barber-application")
    @ResponseStatus(HttpStatus.CREATED)
    public BarberApplicationResponse submitBarberApplication(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody BarberApplicationCreateRequest request
    ) {
        return barberApplicationService.submit(authenticatedUser.userId(), request);
    }

    @GetMapping("/barber-applications")
    public List<BarberApplicationResponse> findBarberApplications(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return barberApplicationService.findForUser(authenticatedUser.userId());
    }
}
