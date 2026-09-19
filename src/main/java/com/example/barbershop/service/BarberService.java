package com.example.barbershop.service;

import com.example.barbershop.dto.BarberCreateRequest;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.repository.BarberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BarberService {

    private final BarberRepository barberRepository;
    private final BarberScheduleValidator scheduleValidator;

    public BarberService(
            BarberRepository barberRepository,
            BarberScheduleValidator scheduleValidator
    ) {
        this.barberRepository = barberRepository;
        this.scheduleValidator = scheduleValidator;
    }

    @Transactional
    public BarberResponse create(BarberCreateRequest request) {
        scheduleValidator.validate(request.workStartTime(), request.workEndTime());
        Barber barber = new Barber(
                request.name(),
                request.phone(),
                request.workStartTime(),
                request.workEndTime()
        );
        return toResponse(barberRepository.save(barber));
    }

    @Transactional(readOnly = true)
    public List<BarberResponse> findAll() {
        return barberRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private BarberResponse toResponse(Barber barber) {
        return new BarberResponse(
                barber.getId(),
                barber.getName(),
                barber.getPhone(),
                barber.getWorkStartTime(),
                barber.getWorkEndTime()
        );
    }

}
