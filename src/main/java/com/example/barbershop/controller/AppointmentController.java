package com.example.barbershop.controller;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentRescheduleRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(
            @Valid @RequestBody AppointmentCreateRequest request
    ) {
        return appointmentService.create(request);
    }

    @PatchMapping("/{appointmentId}/cancel")
    public AppointmentResponse cancel(@PathVariable Long appointmentId) {
        return appointmentService.cancel(appointmentId);
    }

    @PatchMapping("/{appointmentId}/arrive")
    public AppointmentResponse markArrived(@PathVariable Long appointmentId) {
        return appointmentService.markArrived(appointmentId);
    }

    @PatchMapping("/{appointmentId}/complete")
    public AppointmentResponse complete(@PathVariable Long appointmentId) {
        return appointmentService.complete(appointmentId);
    }

    @PatchMapping("/{appointmentId}/no-show")
    public AppointmentResponse markNoShow(@PathVariable Long appointmentId) {
        return appointmentService.markNoShow(appointmentId);
    }

    @PatchMapping("/{appointmentId}/reschedule")
    public AppointmentResponse reschedule(
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentRescheduleRequest request
    ) {
        return appointmentService.reschedule(appointmentId, request);
    }
}
