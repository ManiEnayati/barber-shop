package com.example.barbershop.controller;

import com.example.barbershop.dto.AppointmentDelayRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.BarberCancellationRequest;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.BarberAppointmentManagementService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/barber/appointments")
public class BarberAppointmentManagementController {

    private final BarberAppointmentManagementService managementService;

    public BarberAppointmentManagementController(BarberAppointmentManagementService managementService) {
        this.managementService = managementService;
    }

    @PatchMapping("/{appointmentId}/arrive")
    public AppointmentResponse arrive(@AuthenticationPrincipal AuthenticatedUser user,
                                      @PathVariable Long appointmentId) {
        return managementService.arrive(user.userId(), appointmentId);
    }

    @PatchMapping("/{appointmentId}/complete")
    public AppointmentResponse complete(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable Long appointmentId) {
        return managementService.complete(user.userId(), appointmentId);
    }

    @PatchMapping("/{appointmentId}/no-show")
    public AppointmentResponse markNoShow(@AuthenticationPrincipal AuthenticatedUser user,
                                          @PathVariable Long appointmentId) {
        return managementService.markNoShow(user.userId(), appointmentId);
    }

    @PatchMapping("/{appointmentId}/delay")
    public AppointmentResponse updateDelay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentDelayRequest request
    ) {
        return managementService.updateDelay(
                user.userId(), appointmentId, request.delayMinutes());
    }

    @DeleteMapping("/{appointmentId}/delay")
    public AppointmentResponse removeDelay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long appointmentId
    ) {
        return managementService.removeDelay(user.userId(), appointmentId);
    }

    @PatchMapping("/{appointmentId}/cancel")
    public AppointmentResponse cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                      @PathVariable Long appointmentId,
                                      @Valid @RequestBody BarberCancellationRequest request) {
        return managementService.cancel(user.userId(), appointmentId, request.reason());
    }
}
