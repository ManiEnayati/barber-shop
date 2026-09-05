package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDate;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BarberRepository barberRepository;

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
}