package com.example.barbershop.service;

import com.example.barbershop.dto.BarberServiceCreateRequest;
import com.example.barbershop.dto.BarberServiceResponse;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.InvalidBarberServiceException;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BarberServiceOfferingService {

    private final BarberServiceOfferingRepository barberServiceOfferingRepository;
    private final BarberRepository barberRepository;

    public BarberServiceOfferingService(
            BarberServiceOfferingRepository barberServiceOfferingRepository,
            BarberRepository barberRepository
    ) {
        this.barberServiceOfferingRepository = barberServiceOfferingRepository;
        this.barberRepository = barberRepository;
    }

    @Transactional
    public BarberServiceResponse create(BarberServiceCreateRequest request) {
        Barber barber = barberRepository.findById(request.barberId())
                .orElseThrow(() -> new BarberNotFoundException(request.barberId()));

        validateService(request.name(), request.durationMinutes(), request.price());

        BarberServiceOffering serviceOffering = new BarberServiceOffering(
                barber,
                request.name(),
                request.durationMinutes(),
                request.price()
        );

        return toResponse(barberServiceOfferingRepository.save(serviceOffering));
    }

    @Transactional(readOnly = true)
    public List<BarberServiceResponse> findByBarber(Long barberId) {
        barberRepository.findById(barberId)
                .orElseThrow(() -> new BarberNotFoundException(barberId));

        return barberServiceOfferingRepository.findByBarberId(barberId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateService(String name, Integer durationMinutes, Long price) {
        if (name == null
                || name.isBlank()
                || durationMinutes == null
                || durationMinutes <= 0
                || durationMinutes % 30 != 0
                || price == null
                || price < 0) {
            throw new InvalidBarberServiceException();
        }
    }

    private BarberServiceResponse toResponse(BarberServiceOffering serviceOffering) {
        return new BarberServiceResponse(
                serviceOffering.getId(),
                serviceOffering.getBarber().getId(),
                serviceOffering.getBarber().getName(),
                serviceOffering.getName(),
                serviceOffering.getDurationMinutes(),
                serviceOffering.getPrice()
        );
    }
}
