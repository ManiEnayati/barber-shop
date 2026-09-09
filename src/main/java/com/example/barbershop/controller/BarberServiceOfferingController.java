package com.example.barbershop.controller;

import com.example.barbershop.dto.BarberServiceCreateRequest;
import com.example.barbershop.dto.BarberServiceResponse;
import com.example.barbershop.service.BarberServiceOfferingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/barber-services")
public class BarberServiceOfferingController {

    private final BarberServiceOfferingService barberServiceOfferingService;

    public BarberServiceOfferingController(
            BarberServiceOfferingService barberServiceOfferingService
    ) {
        this.barberServiceOfferingService = barberServiceOfferingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BarberServiceResponse create(
            @Valid @RequestBody BarberServiceCreateRequest request
    ) {
        return barberServiceOfferingService.create(request);
    }

    @GetMapping
    public List<BarberServiceResponse> findByBarber(@RequestParam Long barberId) {
        return barberServiceOfferingService.findByBarber(barberId);
    }
}
