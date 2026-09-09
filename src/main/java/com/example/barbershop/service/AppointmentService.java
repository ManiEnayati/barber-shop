package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.AvailableTimeResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.InvalidAppointmentTimeException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BarberRepository barberRepository;
    private static final int SLOT_MINUTES = 30;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            BarberRepository barberRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.barberRepository = barberRepository;
    }

    @Transactional
    public AppointmentResponse create(AppointmentCreateRequest request) {

        Barber barber = barberRepository.findById(request.barberId())
                .orElseThrow(() -> new BarberNotFoundException(request.barberId()));

        validateAppointmentTime(barber, request.time());

        boolean alreadyBooked =
                appointmentRepository.existsByBarberIdAndDateAndTime(
                        request.barberId(),
                        request.date(),
                        request.time()
                );

        if (alreadyBooked) {
            throw new AppointmentSlotAlreadyBookedException();
        }

        Appointment appointment = new Appointment(
                barber,
                request.date(),
                request.time(),
                request.clientName()
        );

        Appointment savedAppointment =
                appointmentRepository.save(appointment);

        return toResponse(savedAppointment);
    }


    private AppointmentResponse toResponse(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getBarber().getId(),
                appointment.getBarber().getName(),
                appointment.getDate(),
                appointment.getTime(),
                appointment.getClientName()
        );
    }
    @Transactional(readOnly = true)
    public List<AppointmentResponse> findByBarberAndDate(
            Long barberId,
            LocalDate date
    ) {
        barberRepository.findById(barberId)
                .orElseThrow(() -> new BarberNotFoundException(barberId));

        return appointmentRepository.findByBarberIdAndDate(barberId, date)
                .stream()
                .map(this::toResponse)
                .toList();
    }



    @Transactional(readOnly = true)
    public List<AvailableTimeResponse> findAvailableTimes(Long barberId, LocalDate date) {

        Barber barber = barberRepository.findById(barberId)
                .orElseThrow(() -> new BarberNotFoundException(barberId));

        List<LocalTime> legalSlotStartTimes = generateLegalSlotStartTimes(barber);

        List<Appointment> appointments =
                appointmentRepository.findByBarberIdAndDate(barberId, date);

        Set<LocalTime> bookedTimes = appointments.stream()
                .map(Appointment::getTime)
                .collect(Collectors.toSet());

        return legalSlotStartTimes.stream()
                .filter(startTime -> !bookedTimes.contains(startTime))
                .map(startTime -> new AvailableTimeResponse(
                        startTime,
                        startTime.plusMinutes(SLOT_MINUTES)
                ))
                .toList();
    }

    private void validateAppointmentTime(Barber barber, LocalTime time) {
        if (!generateLegalSlotStartTimes(barber).contains(time)) {
            throw new InvalidAppointmentTimeException();
        }
    }

    private List<LocalTime> generateLegalSlotStartTimes(Barber barber) {
        List<LocalTime> slotStartTimes = new ArrayList<>();
        LocalTime currentTime = barber.getWorkStartTime();

        while (currentTime.isBefore(barber.getWorkEndTime())) {
            LocalTime slotEndTime = currentTime.plusMinutes(SLOT_MINUTES);
            if (!slotEndTime.isAfter(currentTime)
                    || slotEndTime.isAfter(barber.getWorkEndTime())) {
                break;
            }
            slotStartTimes.add(currentTime);
            currentTime = slotEndTime;
        }

        return slotStartTimes;
    }
}
