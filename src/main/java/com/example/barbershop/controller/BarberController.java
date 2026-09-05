package com.example.barbershop.controller;

import com.example.barbershop.dto.BarberCreateRequest;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.service.BarberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.service.AppointmentService;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BarberResponse create(@Valid @RequestBody BarberCreateRequest request) {
        return barberService.create(request);
    }

    @GetMapping
    public List<BarberResponse> findAll() {
        return barberService.findAll();
    }

    @GetMapping("/{barberId}/appointments")
    public List<AppointmentResponse> findAppointmentsByDate(
            @PathVariable Long barberId,
            @RequestParam LocalDate date
    ) {
        return appointmentService.findByBarberAndDate(barberId, date);
    }
}
