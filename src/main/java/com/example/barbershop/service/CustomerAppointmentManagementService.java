package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.CustomerAppointmentBookingRequest;
import com.example.barbershop.dto.CustomerAppointmentRescheduleRequest;
import com.example.barbershop.dto.AppointmentNoShowReportResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentConfirmation;
import com.example.barbershop.entity.AppointmentEventType;
import com.example.barbershop.entity.AppointmentHistory;
import com.example.barbershop.entity.AppointmentHistoryAction;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.CancellationReason;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.NoShowCustomerResponse;
import com.example.barbershop.exception.AppointmentCannotBeCancelledException;
import com.example.barbershop.exception.AppointmentCannotBeRescheduledException;
import com.example.barbershop.exception.AppointmentNotFoundException;
import com.example.barbershop.repository.AppointmentConfirmationRepository;
import com.example.barbershop.repository.AppointmentHistoryRepository;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.CustomerRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CustomerAppointmentManagementService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository historyRepository;
    private final AppointmentConfirmationRepository confirmationRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentEventService eventService;
    private final AppointmentService appointmentService;
    private final CustomerAppointmentChangePolicy changePolicy;
    private final MeService meService;
    private final Clock clock;
    private final ReputationService reputationService;
    private final AppointmentNoShowReviewService noShowReviewService;

    public CustomerAppointmentManagementService(
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository historyRepository,
            AppointmentConfirmationRepository confirmationRepository,
            CustomerRepository customerRepository,
            AppointmentEventService eventService,
            AppointmentService appointmentService,
            CustomerAppointmentChangePolicy changePolicy,
            MeService meService,
            Clock clock,
            ReputationService reputationService,
            AppointmentNoShowReviewService noShowReviewService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.historyRepository = historyRepository;
        this.confirmationRepository = confirmationRepository;
        this.customerRepository = customerRepository;
        this.eventService = eventService;
        this.appointmentService = appointmentService;
        this.changePolicy = changePolicy;
        this.meService = meService;
        this.clock = clock;
        this.reputationService = reputationService;
        this.noShowReviewService = noShowReviewService;
    }

    @Transactional
    public AppointmentResponse book(
            Long userId,
            CustomerAppointmentBookingRequest request
    ) {
        Customer customer = requireCurrentCustomer(userId);
        return appointmentService.createCustomerBooking(customer, request);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> findOwnAppointments(Long userId) {
        Customer customer = requireCurrentCustomer(userId);
        return appointmentRepository
                .findByCustomerIdOrderByDateDescTimeDescIdDesc(customer.getId())
                .stream()
                .map(appointmentService::toResponse)
                .toList();
    }

    @Transactional
    public AppointmentResponse reschedule(
            Long userId,
            Long appointmentId,
            CustomerAppointmentRescheduleRequest request
    ) {
        appointmentService.expirePendingConfirmations();
        Appointment appointment = requireOwnedAppointment(userId, appointmentId);
        requireReschedulableBooking(appointment);
        changePolicy.requireRescheduleAllowed(
                appointment, LocalDateTime.now(clock));
        appointmentService.validateCustomerRescheduleSlot(
                appointment, request.date(), request.time());

        boolean delayRemedy = appointment.isBarberDelayRemedyAvailable();
        String oldValue = scheduleValue(appointment);
        appointment.rescheduleByCustomer(request.date(), request.time());
        String newValue = scheduleValue(appointment)
                + ",rescheduleType="
                + (delayRemedy ? "BARBER_DELAY_REMEDY" : "NORMAL");
        historyRepository.save(new AppointmentHistory(
                appointment,
                AppointmentHistoryAction.APPOINTMENT_RESCHEDULED,
                oldValue,
                newValue
        ));
        eventService.publish(appointment, AppointmentEventType.APPOINTMENT_RESCHEDULED);
        return appointmentService.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(Long userId, Long appointmentId) {
        appointmentService.expirePendingConfirmations();
        Appointment appointment = requireOwnedAppointment(userId, appointmentId);
        requireCancellableBooking(appointment);

        CancellationReason reason = changePolicy.classifyCancellation(
                appointment, LocalDateTime.now(clock));
        appointment.cancelByCustomer(reason);
        if (appointment.getConfirmationStatus() == BookingConfirmationStatus.PENDING) {
            confirmationRepository.findByAppointmentId(appointmentId)
                    .ifPresent(AppointmentConfirmation::invalidate);
        }
        historyRepository.save(new AppointmentHistory(
                appointment,
                AppointmentHistoryAction.CANCELLED,
                "status=BOOKED",
                "status=CANCELLED,reason=" + reason
        ));
        eventService.publish(appointment, AppointmentEventType.APPOINTMENT_CANCELLED);
        reputationService.finalizeOutcome(appointment);
        return appointmentService.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse rejectBooking(Long userId, Long appointmentId) {
        appointmentService.expirePendingConfirmations();
        requireOwnedAppointment(userId, appointmentId);
        return appointmentService.reject(appointmentId);
    }

    @Transactional
    public AppointmentNoShowReportResponse respondToNoShow(
            Long userId,
            Long appointmentId,
            NoShowCustomerResponse response
    ) {
        Appointment appointment = requireOwnedAppointment(userId, appointmentId);
        return noShowReviewService.respond(appointment, response);
    }

    private Appointment requireOwnedAppointment(Long userId, Long appointmentId) {
        Customer customer = requireCurrentCustomer(userId);
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        if (appointment.getCustomer() == null) {
            throw new AccessDeniedException(
                    "Guest appointments cannot use customer appointment operations");
        }
        if (!appointment.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException(
                    "Appointment belongs to another customer");
        }
        return appointment;
    }

    private Customer requireCurrentCustomer(Long userId) {
        meService.requireCustomerUser(userId);
        return customerRepository.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Linked customer profile is required"));
    }

    private void requireReschedulableBooking(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED
                || !appointment.getConfirmationStatus().isActive()) {
            throw new AppointmentCannotBeRescheduledException();
        }
    }

    private void requireCancellableBooking(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED
                || !appointment.getConfirmationStatus().isActive()) {
            throw new AppointmentCannotBeCancelledException();
        }
    }

    private String scheduleValue(Appointment appointment) {
        return "date=" + appointment.getDate()
                + ",time=" + appointment.getTime();
    }
}
