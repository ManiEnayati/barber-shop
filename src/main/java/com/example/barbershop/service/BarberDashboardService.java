package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.BarberCalendarResponse;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BarberDashboardService {

    private final UserRepository userRepository;
    private final BarberRepository barberRepository;
    private final AppointmentService appointmentService;

    public BarberDashboardService(
            UserRepository userRepository,
            BarberRepository barberRepository,
            AppointmentService appointmentService
    ) {
        this.userRepository = userRepository;
        this.barberRepository = barberRepository;
        this.appointmentService = appointmentService;
    }

    @Transactional(readOnly = true)
    public BarberResponse getCurrentBarber(Long userId) {
        return toResponse(requireCurrentBarber(userId));
    }

    @Transactional
    public BarberCalendarResponse getCalendar(Long userId, LocalDate date) {
        Barber barber = requireCurrentBarber(userId);
        return new BarberCalendarResponse(
                date,
                barber.getWorkStartTime(),
                barber.getWorkEndTime(),
                appointmentService.findCalendarSlots(barber, date)
        );
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointments(
            Long userId,
            LocalDate date,
            AppointmentStatus status
    ) {
        Barber barber = requireCurrentBarber(userId);
        return appointmentService.findByBarber(barber.getId(), date, status);
    }

    private Barber requireCurrentBarber(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isPhoneVerified)
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user is unavailable"
                ));
        if (!user.getRoles().contains(UserRole.BARBER)) {
            throw new AccessDeniedException("Barber role is required");
        }
        return barberRepository.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Linked barber profile is required"
                ));
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
