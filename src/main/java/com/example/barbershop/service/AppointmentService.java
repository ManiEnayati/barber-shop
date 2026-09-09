package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.AvailableTimeResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.BarberServiceDoesNotBelongToBarberException;
import com.example.barbershop.exception.BarberServiceOfferingNotFoundException;
import com.example.barbershop.exception.InvalidAppointmentTimeException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AppointmentService {

    private static final int SLOT_MINUTES = 30;

    private final AppointmentRepository appointmentRepository;
    private final BarberRepository barberRepository;
    private final BarberServiceOfferingRepository barberServiceOfferingRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            BarberRepository barberRepository,
            BarberServiceOfferingRepository barberServiceOfferingRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.barberRepository = barberRepository;
        this.barberServiceOfferingRepository = barberServiceOfferingRepository;
    }

    @Transactional
    public AppointmentResponse create(AppointmentCreateRequest request) {
        Barber barber = barberRepository.findById(request.barberId())
                .orElseThrow(() -> new BarberNotFoundException(request.barberId()));
        BarberServiceOffering serviceOffering = findServiceOffering(request.serviceId());

        validateServiceBelongsToBarber(serviceOffering, request.barberId());
        validateAppointmentTime(barber, serviceOffering, request.time());

        List<Appointment> existingAppointments =
                appointmentRepository.findByBarberIdAndDate(
                        request.barberId(),
                        request.date()
                );

        if (overlapsAnyAppointment(
                request.time(),
                serviceOffering.getDurationMinutes(),
                existingAppointments
        )) {
            throw new AppointmentSlotAlreadyBookedException();
        }

        Appointment appointment = new Appointment(
                barber,
                serviceOffering,
                request.date(),
                request.time(),
                request.clientName()
        );

        return toResponse(appointmentRepository.save(appointment));
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> findByBarberAndDate(
            Long barberId,
            LocalDate date
    ) {
        barberRepository.findById(barberId)
                .orElseThrow(() -> new BarberNotFoundException(barberId));

        return appointmentRepository.findByBarberIdAndDate(barberId, date).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailableTimeResponse> findAvailableTimes(
            Long barberId,
            LocalDate date,
            Long serviceId
    ) {
        Barber barber = barberRepository.findById(barberId)
                .orElseThrow(() -> new BarberNotFoundException(barberId));
        BarberServiceOffering serviceOffering = findServiceOffering(serviceId);

        validateServiceBelongsToBarber(serviceOffering, barberId);

        int durationMinutes = serviceOffering.getDurationMinutes();
        List<LocalTime> candidateStartTimes = generateLegalSlotStartTimes(
                barber,
                durationMinutes
        );
        List<Appointment> existingAppointments =
                appointmentRepository.findByBarberIdAndDate(barberId, date);

        return candidateStartTimes.stream()
                .filter(startTime -> !overlapsAnyAppointment(
                        startTime,
                        durationMinutes,
                        existingAppointments
                ))
                .map(startTime -> new AvailableTimeResponse(
                        startTime,
                        startTime.plusMinutes(durationMinutes)
                ))
                .toList();
    }

    private BarberServiceOffering findServiceOffering(Long serviceId) {
        return barberServiceOfferingRepository.findById(serviceId)
                .orElseThrow(() -> new BarberServiceOfferingNotFoundException(serviceId));
    }

    private void validateServiceBelongsToBarber(
            BarberServiceOffering serviceOffering,
            Long barberId
    ) {
        if (!Objects.equals(serviceOffering.getBarber().getId(), barberId)) {
            throw new BarberServiceDoesNotBelongToBarberException();
        }
    }

    private void validateAppointmentTime(
            Barber barber,
            BarberServiceOffering serviceOffering,
            LocalTime time
    ) {
        if (!generateLegalSlotStartTimes(
                barber,
                serviceOffering.getDurationMinutes()
        ).contains(time)) {
            throw new InvalidAppointmentTimeException();
        }
    }

    private List<LocalTime> generateLegalSlotStartTimes(
            Barber barber,
            int durationMinutes
    ) {
        List<LocalTime> startTimes = new ArrayList<>();
        LocalTime currentTime = barber.getWorkStartTime();

        while (Duration.between(currentTime, barber.getWorkEndTime()).toMinutes()
                >= durationMinutes) {
            startTimes.add(currentTime);
            currentTime = currentTime.plusMinutes(SLOT_MINUTES);
        }

        return startTimes;
    }

    private boolean overlapsAnyAppointment(
            LocalTime newStart,
            int durationMinutes,
            List<Appointment> existingAppointments
    ) {
        LocalTime newEnd = newStart.plusMinutes(durationMinutes);

        return existingAppointments.stream().anyMatch(existingAppointment -> {
            LocalTime existingStart = existingAppointment.getTime();
            LocalTime existingEnd = existingStart.plusMinutes(
                    existingAppointment.getServiceOffering().getDurationMinutes()
            );
            return newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart);
        });
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        BarberServiceOffering serviceOffering = appointment.getServiceOffering();
        LocalTime endTime = appointment.getTime().plusMinutes(
                serviceOffering.getDurationMinutes()
        );

        return new AppointmentResponse(
                appointment.getId(),
                appointment.getBarber().getId(),
                appointment.getBarber().getName(),
                serviceOffering.getId(),
                serviceOffering.getName(),
                serviceOffering.getDurationMinutes(),
                appointment.getDate(),
                appointment.getTime(),
                endTime,
                appointment.getClientName()
        );
    }
}
