package com.example.barbershop.controller;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.CustomerAppointmentRescheduleRequest;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.CustomerAppointmentManagementService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/appointments")
public class CustomerAppointmentManagementController {

    private final CustomerAppointmentManagementService managementService;

    public CustomerAppointmentManagementController(
            CustomerAppointmentManagementService managementService
    ) {
        this.managementService = managementService;
    }

    @PatchMapping("/{appointmentId}/reschedule")
    public AppointmentResponse reschedule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long appointmentId,
            @Valid @RequestBody CustomerAppointmentRescheduleRequest request
    ) {
        return managementService.reschedule(user.userId(), appointmentId, request);
    }

    @PatchMapping("/{appointmentId}/cancel")
    public AppointmentResponse cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long appointmentId
    ) {
        return managementService.cancel(user.userId(), appointmentId);
    }
}
