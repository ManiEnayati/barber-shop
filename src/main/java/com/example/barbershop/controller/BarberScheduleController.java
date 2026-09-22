package com.example.barbershop.controller;

import com.example.barbershop.dto.BarberWeeklyScheduleRequest;
import com.example.barbershop.dto.BarberWeeklyScheduleResponse;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.BarberScheduleService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me/barber/schedule")
public class BarberScheduleController {

    private final BarberScheduleService scheduleService;

    public BarberScheduleController(BarberScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public List<BarberWeeklyScheduleResponse> get(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return scheduleService.getCurrent(user.userId());
    }

    @PutMapping
    public List<BarberWeeklyScheduleResponse> replace(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody List<@Valid BarberWeeklyScheduleRequest> request) {
        return scheduleService.replace(user.userId(), request);
    }
}
