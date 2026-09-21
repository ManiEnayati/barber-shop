package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentConfirmation;
import com.example.barbershop.entity.AppointmentEventType;
import com.example.barbershop.entity.AppointmentHistory;
import com.example.barbershop.entity.AppointmentHistoryAction;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.exception.AppointmentCannotBeCompletedException;
import com.example.barbershop.exception.AppointmentCannotBeMarkedArrivedException;
import com.example.barbershop.exception.AppointmentCannotBeMarkedNoShowException;
import com.example.barbershop.exception.AppointmentNotFoundException;
import com.example.barbershop.repository.AppointmentConfirmationRepository;
import com.example.barbershop.repository.AppointmentHistoryRepository;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BarberAppointmentManagementService {

    private final UserRepository userRepository;
    private final BarberRepository barberRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository historyRepository;
    private final AppointmentConfirmationRepository confirmationRepository;
    private final AppointmentEventService eventService;
    private final AppointmentService appointmentService;

    public BarberAppointmentManagementService(
            UserRepository userRepository,
            BarberRepository barberRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository historyRepository,
            AppointmentConfirmationRepository confirmationRepository,
            AppointmentEventService eventService,
            AppointmentService appointmentService
    ) {
        this.userRepository = userRepository;
        this.barberRepository = barberRepository;
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
        this.confirmationRepository = confirmationRepository;
        this.eventService = eventService;
        this.appointmentService = appointmentService;
    }

    @Transactional
    public AppointmentResponse arrive(Long userId, Long appointmentId) {
        Appointment appointment = requireOwnedAppointment(userId, appointmentId);
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new AppointmentCannotBeMarkedArrivedException();
        }
        appointment.arrive();
        record(appointment, AppointmentHistoryAction.CUSTOMER_ARRIVED,
                "status=BOOKED", "status=ARRIVED");
        return appointmentService.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse complete(Long userId, Long appointmentId) {
        Appointment appointment = requireOwnedAppointment(userId, appointmentId);
        if (appointment.getStatus() != AppointmentStatus.ARRIVED) {
            throw new AppointmentCannotBeCompletedException();
        }
        appointment.complete();
        record(appointment, AppointmentHistoryAction.APPOINTMENT_COMPLETED,
                "status=ARRIVED", "status=COMPLETED");
        return appointmentService.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse markNoShow(Long userId, Long appointmentId) {
        Appointment appointment = requireOwnedAppointment(userId, appointmentId);
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new AppointmentCannotBeMarkedNoShowException();
        }
        appointment.markNoShow();
        record(appointment, AppointmentHistoryAction.CUSTOMER_NO_SHOW,
                "status=BOOKED", "status=NO_SHOW");
        return appointmentService.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(Long userId, Long appointmentId, String reason) {
        Appointment appointment = requireOwnedAppointment(userId, appointmentId);
        appointment.cancelByBarber(reason);
        if (appointment.getConfirmationStatus() == BookingConfirmationStatus.PENDING) {
            confirmationRepository.findByAppointmentId(appointmentId)
                    .ifPresent(AppointmentConfirmation::invalidate);
        }
        record(appointment, AppointmentHistoryAction.BARBER_CANCELLED,
                "status=BOOKED", "status=CANCELLED,reason=" + appointment.getCancellationNote());
        eventService.publish(appointment, AppointmentEventType.APPOINTMENT_CANCELLED);
        return appointmentService.toResponse(appointment);
    }

    private Appointment requireOwnedAppointment(Long userId, Long appointmentId) {
        Barber barber = requireCurrentBarber(userId);
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        if (!appointment.getBarber().getId().equals(barber.getId())) {
            throw new AccessDeniedException("Appointment belongs to another barber");
        }
        return appointment;
    }

    private Barber requireCurrentBarber(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isPhoneVerified)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is unavailable"));
        if (!user.getRoles().contains(UserRole.BARBER)) {
            throw new AccessDeniedException("Barber role is required");
        }
        return barberRepository.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException("Linked barber profile is required"));
    }

    private void record(Appointment appointment, AppointmentHistoryAction action,
                        String oldValue, String newValue) {
        historyRepository.save(new AppointmentHistory(appointment, action, oldValue, newValue));
    }
}
