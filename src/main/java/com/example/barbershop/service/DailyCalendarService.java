package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.BlockedTimeResponse;
import com.example.barbershop.dto.DailyCalendarResponse;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.repository.BarberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class DailyCalendarService {

    private final BarberRepository barberRepository;
    private final AppointmentService appointmentService;
    private final BlockedTimeService blockedTimeService;

    public DailyCalendarService(
            BarberRepository barberRepository,
            AppointmentService appointmentService,
            BlockedTimeService blockedTimeService
    ) {
        this.barberRepository = barberRepository;
        this.appointmentService = appointmentService;
        this.blockedTimeService = blockedTimeService;
    }

    @Transactional(readOnly = true)
    public DailyCalendarResponse getDailyCalendar(Long barberId, LocalDate date) {
        Barber barber = barberRepository.findById(barberId)
                .orElseThrow(() -> new BarberNotFoundException(barberId));

        List<AppointmentResponse> appointments =
                appointmentService.findByBarberAndDate(barberId, date).stream()
                        .sorted(Comparator.comparing(AppointmentResponse::time))
                        .toList();
        List<BlockedTimeResponse> blockedTimes =
                blockedTimeService.findByBarberAndDate(barberId, date).stream()
                        .sorted(Comparator.comparing(BlockedTimeResponse::startTime))
                        .toList();

        return new DailyCalendarResponse(
                barber.getId(),
                barber.getName(),
                date,
                barber.getWorkStartTime(),
                barber.getWorkEndTime(),
                appointments,
                blockedTimes
        );
    }
}
