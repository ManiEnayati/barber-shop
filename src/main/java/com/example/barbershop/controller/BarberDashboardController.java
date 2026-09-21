package com.example.barbershop.controller;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.BarberCalendarResponse;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.BarberDashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me/barber")
public class BarberDashboardController {

    private final BarberDashboardService barberDashboardService;

    public BarberDashboardController(BarberDashboardService barberDashboardService) {
        this.barberDashboardService = barberDashboardService;
    }

    @GetMapping
    public BarberResponse getCurrentBarber(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return barberDashboardService.getCurrentBarber(authenticatedUser.userId());
    }

    @GetMapping("/calendar")
    public BarberCalendarResponse getCalendar(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam LocalDate date
    ) {
        return barberDashboardService.getCalendar(authenticatedUser.userId(), date);
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> getAppointments(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) AppointmentStatus status
    ) {
        return barberDashboardService.getAppointments(
                authenticatedUser.userId(), date, status
        );
    }
}
