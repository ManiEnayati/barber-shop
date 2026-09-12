package com.example.barbershop.service;

import com.example.barbershop.dto.BlockedTimeCreateRequest;
import com.example.barbershop.dto.BlockedTimeResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.BlockedTimeNotFoundException;
import com.example.barbershop.exception.BlockedTimeOverlapsActiveAppointmentException;
import com.example.barbershop.exception.BlockedTimeOverlapsAnotherBlockedTimeException;
import com.example.barbershop.exception.InvalidBlockedTimeException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class BlockedTimeService {

    private final BlockedTimeRepository blockedTimeRepository;
    private final BarberRepository barberRepository;
    private final AppointmentRepository appointmentRepository;

    public BlockedTimeService(
            BlockedTimeRepository blockedTimeRepository,
            BarberRepository barberRepository,
            AppointmentRepository appointmentRepository
    ) {
        this.blockedTimeRepository = blockedTimeRepository;
        this.barberRepository = barberRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public BlockedTimeResponse create(BlockedTimeCreateRequest request) {
        Barber barber = barberRepository.findById(request.barberId())
                .orElseThrow(() -> new BarberNotFoundException(request.barberId()));
        validateRange(barber, request.startTime(), request.endTime());

        List<Appointment> appointments = appointmentRepository.findByBarberIdAndDate(
                request.barberId(), request.date()
        );
        if (appointments.stream()
                .filter(this::isActive)
                .anyMatch(appointment -> TimeIntervals.overlap(
                        request.startTime(),
                        request.endTime(),
                        appointment.getTime(),
                        appointment.getTime().plusMinutes(
                                appointment.getServiceOffering().getDurationMinutes()
                        )
                ))) {
            throw new BlockedTimeOverlapsActiveAppointmentException();
        }

        List<BlockedTime> existingBlocks = blockedTimeRepository.findByBarberIdAndDate(
                request.barberId(), request.date()
        );
        if (existingBlocks.stream().anyMatch(blockedTime -> TimeIntervals.overlap(
                request.startTime(),
                request.endTime(),
                blockedTime.getStartTime(),
                blockedTime.getEndTime()
        ))) {
            throw new BlockedTimeOverlapsAnotherBlockedTimeException();
        }

        BlockedTime blockedTime = new BlockedTime(
                barber,
                request.date(),
                request.startTime(),
                request.endTime(),
                request.reason()
        );
        return toResponse(blockedTimeRepository.save(blockedTime));
    }

    @Transactional(readOnly = true)
    public List<BlockedTimeResponse> findByBarberAndDate(Long barberId, LocalDate date) {
        barberRepository.findById(barberId)
                .orElseThrow(() -> new BarberNotFoundException(barberId));
        return blockedTimeRepository.findByBarberIdAndDate(barberId, date).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long blockedTimeId) {
        BlockedTime blockedTime = blockedTimeRepository.findById(blockedTimeId)
                .orElseThrow(() -> new BlockedTimeNotFoundException(blockedTimeId));
        blockedTimeRepository.delete(blockedTime);
    }

    private void validateRange(Barber barber, LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)
                || startTime.isBefore(barber.getWorkStartTime())
                || endTime.isAfter(barber.getWorkEndTime())
                || !isAlignedToSlotBoundary(startTime)
                || !isAlignedToSlotBoundary(endTime)) {
            throw new InvalidBlockedTimeException();
        }
    }

    private boolean isAlignedToSlotBoundary(LocalTime time) {
        return (time.getMinute() == 0 || time.getMinute() == 30)
                && time.getSecond() == 0
                && time.getNano() == 0;
    }

    private boolean isActive(Appointment appointment) {
        return appointment.getStatus().isActive();
    }

    private BlockedTimeResponse toResponse(BlockedTime blockedTime) {
        return new BlockedTimeResponse(
                blockedTime.getId(),
                blockedTime.getBarber().getId(),
                blockedTime.getBarber().getName(),
                blockedTime.getDate(),
                blockedTime.getStartTime(),
                blockedTime.getEndTime(),
                blockedTime.getReason()
        );
    }
}
