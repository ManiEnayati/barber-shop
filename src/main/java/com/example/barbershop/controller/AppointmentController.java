package com.example.barbershop.controller;

import com.example.barbershop.dto.AppointmentConfirmRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping("/{appointmentId}/confirm")
    public AppointmentResponse confirm(@PathVariable Long appointmentId,
                                       @Valid @RequestBody AppointmentConfirmRequest request) {
        return appointmentService.confirm(appointmentId, request);
    }

}
