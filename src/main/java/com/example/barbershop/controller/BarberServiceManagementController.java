package com.example.barbershop.controller;

import com.example.barbershop.dto.BarberServiceManagementRequest;
import com.example.barbershop.dto.BarberServiceResponse;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.BarberServiceManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/barber/services")
public class BarberServiceManagementController {

    private final BarberServiceManagementService managementService;

    public BarberServiceManagementController(
            BarberServiceManagementService managementService
    ) {
        this.managementService = managementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BarberServiceResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody BarberServiceManagementRequest request
    ) {
        return managementService.create(user.userId(), request);
    }

    @PutMapping("/{serviceId}")
    public BarberServiceResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long serviceId,
            @Valid @RequestBody BarberServiceManagementRequest request
    ) {
        return managementService.update(user.userId(), serviceId, request);
    }

    @DeleteMapping("/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long serviceId
    ) {
        managementService.delete(user.userId(), serviceId);
    }
}
