package com.example.barbershop.service;

import com.example.barbershop.dto.BarberCreateRequest;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.exception.InvalidBarberScheduleException;
import com.example.barbershop.repository.BarberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
public class BarberService {

    private final BarberRepository barberRepository;

    public BarberService(BarberRepository barberRepository) {
        this.barberRepository = barberRepository;
    }

    @Transactional
    public BarberResponse create(BarberCreateRequest request) {
        validateSchedule(request.workStartTime(), request.workEndTime());
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

    private void validateSchedule(LocalTime workStartTime, LocalTime workEndTime) {
        if (workStartTime == null
                || workEndTime == null
                || !workStartTime.isBefore(workEndTime)
                || !isAlignedToSlotBoundary(workStartTime)
                || !isAlignedToSlotBoundary(workEndTime)) {
            throw new InvalidBarberScheduleException();
        }
    }

    private boolean isAlignedToSlotBoundary(LocalTime time) {
        return (time.getMinute() == 0 || time.getMinute() == 30)
                && time.getSecond() == 0
                && time.getNano() == 0;
    }
}
