package com.example.barbershop.service;

import com.example.barbershop.dto.BarberBlockedTimeCreateRequest;
import com.example.barbershop.dto.BlockedTimeCreateRequest;
import com.example.barbershop.dto.BlockedTimeResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.BookingConfirmationStatus;
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
import java.util.ArrayList;
import java.util.List;

@Service
public class BlockedTimeService {

    private final BlockedTimeRepository blockedTimeRepository;
    private final BarberRepository barberRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final BarberScheduleService scheduleService;

    public BlockedTimeService(
            BlockedTimeRepository blockedTimeRepository,
            BarberRepository barberRepository,
            AppointmentRepository appointmentRepository,
            AppointmentService appointmentService,
            BarberScheduleService scheduleService
    ) {
        this.blockedTimeRepository = blockedTimeRepository;
        this.barberRepository = barberRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
        this.scheduleService = scheduleService;
    }

    @Transactional
    public BlockedTimeResponse create(BlockedTimeCreateRequest request) {
        Barber barber = barberRepository.findById(request.barberId())
                .orElseThrow(() -> new BarberNotFoundException(request.barberId()));
        return createForBarber(barber, request.date(), request.startTime(),
                request.endTime(), request.reason());
    }

    @Transactional
    public BlockedTimeResponse createForBarber(Barber barber, LocalDate date,
                                               LocalTime startTime, LocalTime endTime,
                                               String reason) {
        appointmentService.expirePendingConfirmations();
        validateCandidate(barber, date, startTime, endTime, null, List.of());
        BlockedTime blockedTime = new BlockedTime(barber, date, startTime, endTime,
                normalizeReason(reason));
        return toResponse(blockedTimeRepository.save(blockedTime));
    }

    @Transactional
    public List<BlockedTimeResponse> createBulkForBarber(Barber barber,
            List<BarberBlockedTimeCreateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new InvalidBlockedTimeException();
        }
        appointmentService.expirePendingConfirmations();
        List<BlockedTime> candidates = new ArrayList<>();
        for (BarberBlockedTimeCreateRequest request : requests) {
            if (request == null) {
                throw new InvalidBlockedTimeException();
            }
            validateCandidate(barber, request.date(), request.startTime(), request.endTime(),
                    null, candidates);
            candidates.add(new BlockedTime(barber, request.date(), request.startTime(),
                    request.endTime(), normalizeReason(request.reason())));
        }
        return blockedTimeRepository.saveAll(candidates).stream().map(this::toResponse).toList();
    }

    @Transactional
    public BlockedTimeResponse updateForBarber(BlockedTime blockedTime, LocalDate date,
            LocalTime startTime, LocalTime endTime, String reason) {
        appointmentService.expirePendingConfirmations();
        validateCandidate(blockedTime.getBarber(), date, startTime, endTime,
                blockedTime.getId(), List.of());
        String normalizedReason = normalizeReason(reason);
        blockedTime.update(date, startTime, endTime, normalizedReason);
        return toResponse(blockedTimeRepository.save(blockedTime));
    }

    private void validateCandidate(Barber barber, LocalDate date, LocalTime startTime,
            LocalTime endTime, Long excludedId, List<BlockedTime> pending) {
        validateRange(barber, date, startTime, endTime);

        List<Appointment> appointments = appointmentRepository.findByBarberIdAndDate(
                barber.getId(), date
        );
        if (appointments.stream()
                .filter(this::isActive)
                .anyMatch(appointment -> TimeIntervals.overlap(
                        startTime,
                        endTime,
                        appointment.getTime(),
                        appointment.getTime().plusMinutes(
                                appointment.getServiceOffering().getDurationMinutes()
                        )
                ))) {
            throw new BlockedTimeOverlapsActiveAppointmentException();
        }

        List<BlockedTime> existingBlocks = blockedTimeRepository.findByBarberIdAndDate(
                barber.getId(), date
        );
        if (existingBlocks.stream().filter(blockedTime -> excludedId == null
                        || !excludedId.equals(blockedTime.getId()))
                .anyMatch(blockedTime -> TimeIntervals.overlap(
                        startTime, endTime, blockedTime.getStartTime(), blockedTime.getEndTime()))
                || pending.stream().filter(blockedTime -> blockedTime.getDate().equals(date))
                .anyMatch(blockedTime -> TimeIntervals.overlap(startTime, endTime,
                        blockedTime.getStartTime(), blockedTime.getEndTime()))) {
            throw new BlockedTimeOverlapsAnotherBlockedTimeException();
        }
    }

    @Transactional(readOnly = true)
    public List<BlockedTimeResponse> findByBarberAndDate(Long barberId, LocalDate date) {
        barberRepository.findById(barberId)
                .orElseThrow(() -> new BarberNotFoundException(barberId));
        return blockedTimeRepository.findByBarberIdAndDate(barberId, date).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BlockedTimeResponse> findByBarberAndWeek(Long barberId, LocalDate startDate) {
        return blockedTimeRepository.findByBarberIdAndDateBetweenOrderByDateAscStartTimeAsc(
                barberId, startDate, startDate.plusDays(6)).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long blockedTimeId) {
        BlockedTime blockedTime = blockedTimeRepository.findById(blockedTimeId)
                .orElseThrow(() -> new BlockedTimeNotFoundException(blockedTimeId));
        blockedTimeRepository.delete(blockedTime);
    }

    private void validateRange(Barber barber, LocalDate date,
                               LocalTime startTime, LocalTime endTime) {
        if (date == null || startTime == null || endTime == null) {
            throw new InvalidBlockedTimeException();
        }
        var hours = scheduleService.workingHours(barber, date);
        if (hours.isEmpty()
                || !startTime.isBefore(endTime)
                || startTime.isBefore(hours.get().start())
                || endTime.isAfter(hours.get().end())
                || !isAlignedToSlotBoundary(startTime)
                || !isAlignedToSlotBoundary(endTime)) {
            throw new InvalidBlockedTimeException();
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.length() > 255) {
            throw new InvalidBlockedTimeException();
        }
        return normalized;
    }

    private boolean isAlignedToSlotBoundary(LocalTime time) {
        return (time.getMinute() == 0 || time.getMinute() == 30)
                && time.getSecond() == 0
                && time.getNano() == 0;
    }

    private boolean isActive(Appointment appointment) {
        return appointment.getStatus().isActive()
                && appointment.getConfirmationStatus() != BookingConfirmationStatus.EXPIRED
                && appointment.getConfirmationStatus() != BookingConfirmationStatus.REJECTED;
    }

    private BlockedTimeResponse toResponse(BlockedTime blockedTime) {
        return new BlockedTimeResponse(
                blockedTime.getId(),
                blockedTime.getBarber().getId(),
                blockedTime.getBarber().getName(),
                blockedTime.getDate(),
                blockedTime.getStartTime(),
                blockedTime.getEndTime(),
                blockedTime.getReason(),
                blockedTime.getCreatedAt()
        );
    }
}
