package com.example.barbershop.controller;

import com.example.barbershop.dto.AvailableTimeResponse;
import com.example.barbershop.dto.PublicBarberResponse;
import com.example.barbershop.service.AppointmentService;
import com.example.barbershop.service.BarberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/barbers")
public class BarberController {

    private final BarberService barberService;
    private final AppointmentService appointmentService;

    public BarberController(
            BarberService barberService,
            AppointmentService appointmentService
    ) {
        this.barberService = barberService;
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<PublicBarberResponse> findAll() {
        return barberService.findAll().stream()
                .map(PublicBarberResponse::from)
                .toList();
    }

    @GetMapping("/{barberId}/available-times")
    public List<AvailableTimeResponse> findAvailableTimes(
            @PathVariable Long barberId,
            @RequestParam LocalDate date,
            @RequestParam Long serviceId
    ) {
        return appointmentService.findAvailableTimes(barberId, date, serviceId);
    }
}
