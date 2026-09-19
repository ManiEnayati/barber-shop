package com.example.barbershop.controller;

import com.example.barbershop.dto.AdminBarberApplicationResponse;
import com.example.barbershop.dto.BarberApplicationRejectRequest;
import com.example.barbershop.entity.BarberApplicationStatus;
import com.example.barbershop.service.BarberApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/barber-applications")
public class AdminBarberApplicationController {

    private final BarberApplicationService barberApplicationService;

    public AdminBarberApplicationController(
            BarberApplicationService barberApplicationService
    ) {
        this.barberApplicationService = barberApplicationService;
    }

    @GetMapping
    public List<AdminBarberApplicationResponse> findAll(
            @RequestParam(required = false) BarberApplicationStatus status
    ) {
        return barberApplicationService.findAll(status);
    }

    @PostMapping("/{applicationId}/approve")
    public AdminBarberApplicationResponse approve(
            @PathVariable Long applicationId
    ) {
        return barberApplicationService.approve(applicationId);
    }

    @PostMapping("/{applicationId}/reject")
    public AdminBarberApplicationResponse reject(
            @PathVariable Long applicationId,
            @Valid @RequestBody BarberApplicationRejectRequest request
    ) {
        return barberApplicationService.reject(applicationId, request.note());
    }
}
