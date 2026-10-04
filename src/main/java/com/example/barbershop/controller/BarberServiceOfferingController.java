package com.example.barbershop.controller;

import com.example.barbershop.dto.BarberServiceResponse;
import com.example.barbershop.service.BarberServiceOfferingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public List<BarberServiceResponse> findByBarber(@RequestParam Long barberId) {
        return barberServiceOfferingService.findByBarber(barberId);
    }
}
