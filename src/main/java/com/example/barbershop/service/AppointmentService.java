package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentConfirmRequest;
import com.example.barbershop.dto.AppointmentRescheduleRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.AvailableTimeResponse;
import com.example.barbershop.dto.CancellationRequest;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentConfirmation;
import com.example.barbershop.entity.AppointmentHistory;
import com.example.barbershop.entity.AppointmentHistoryAction;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.BookingSource;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.AppointmentNotFoundException;
import com.example.barbershop.exception.AppointmentOverlapsBlockedTimeException;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
import com.example.barbershop.exception.ConfirmationCodeExpiredException;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.BarberServiceDoesNotBelongToBarberException;
import com.example.barbershop.exception.BarberServiceOfferingNotFoundException;
import com.example.barbershop.exception.CustomerNotFoundException;
import com.example.barbershop.exception.InvalidAppointmentTimeException;
import com.example.barbershop.exception.InvalidAppointmentConfirmationException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.AppointmentHistoryRepository;
import com.example.barbershop.repository.AppointmentConfirmationRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import com.example.barbershop.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class AppointmentService {

    private static final int SLOT_MINUTES = 30;
    private static final int CONFIRMATION_MINUTES = 15;
    private static final SecureRandom CODE_RANDOM = new SecureRandom();

    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final AppointmentConfirmationRepository appointmentConfirmationRepository;
    private final BarberRepository barberRepository;
    private final BarberServiceOfferingRepository barberServiceOfferingRepository;
    private final BlockedTimeRepository blockedTimeRepository;
    private final CustomerRepository customerRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            AppointmentConfirmationRepository appointmentConfirmationRepository,
            BarberRepository barberRepository,
            BarberServiceOfferingRepository barberServiceOfferingRepository,
            BlockedTimeRepository blockedTimeRepository,
            CustomerRepository customerRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.appointmentConfirmationRepository = appointmentConfirmationRepository;
        this.barberRepository = barberRepository;
        this.barberServiceOfferingRepository = barberServiceOfferingRepository;
        this.blockedTimeRepository = blockedTimeRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public AppointmentResponse create(AppointmentCreateRequest request) {
        Barber barber = barberRepository.findById(request.barberId())
                .orElseThrow(() -> new BarberNotFoundException(request.barberId()));
        BarberServiceOffering serviceOffering = findServiceOffering(request.serviceId());
        Customer customer = request.customerId() == null ? null : customerRepository
                .findById(request.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(request.customerId()));

        validateServiceBelongsToBarber(serviceOffering, request.barberId());
        validateAppointmentTime(barber, serviceOffering, request.time());
        expirePendingConfirmations();

        List<Appointment> existingAppointments =
                appointmentRepository.findByBarberIdAndDate(
                        request.barberId(),
                        request.date()
                );

        if (overlapsAnyActiveAppointment(
                request.time(),
                serviceOffering.getDurationMinutes(),
                existingAppointments,
                null
        )) {
            throw new AppointmentSlotAlreadyBookedException();
        }

        rejectBlockedTimeOverlap(
                request.barberId(),
                request.date(),
                request.time(),
                serviceOffering.getDurationMinutes()
        );

        BookingSource source = request.source() == null
                ? BookingSource.CUSTOMER : request.source();
        Appointment appointment = customer != null
                ? new Appointment(barber, serviceOffering, customer, source,
                        request.date(), request.time())
                : new Appointment(barber, serviceOffering, request.guestName(),
                        request.guestPhone(), request.date(), request.time());

        Appointment saved = appointmentRepository.save(appointment);
        recordHistory(saved, AppointmentHistoryAction.CREATED,
                null, creationValue(saved));
        if (saved.getConfirmationStatus() == BookingConfirmationStatus.PENDING) {
            AppointmentConfirmation confirmation = new AppointmentConfirmation(
                    saved, newConfirmationCode(),
                    LocalDateTime.now().plusMinutes(CONFIRMATION_MINUTES));
            appointmentConfirmationRepository.save(confirmation);
            recordHistory(saved, AppointmentHistoryAction.CONFIRMATION_CREATED,
                    null, "confirmationStatus=PENDING,expiresAt="
                            + confirmation.getExpiresAt());
        }
        return toResponse(saved);
    }

    @Transactional(noRollbackFor = ConfirmationCodeExpiredException.class)
    public AppointmentResponse confirm(Long appointmentId,
                                       AppointmentConfirmRequest request) {
        Appointment appointment = findAppointment(appointmentId);
        requirePendingConfirmation(appointment);
        AppointmentConfirmation confirmation = findConfirmation(appointmentId);
        LocalDateTime now = LocalDateTime.now();
        if (confirmation.isExpired(now)) {
            expireConfirmation(confirmation);
            throw new ConfirmationCodeExpiredException();
        }
        if (!confirmation.matchesCode(request.code())) {
            throw new InvalidAppointmentConfirmationException("Confirmation code is incorrect");
        }
        confirmation.markConfirmed(now);
        appointment.confirmBooking();
        recordHistory(appointment, AppointmentHistoryAction.CONFIRMED,
                "confirmationStatus=PENDING", "confirmationStatus=CONFIRMED");
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse reject(Long appointmentId) {
        Appointment appointment = findAppointment(appointmentId);
        requirePendingConfirmation(appointment);
        AppointmentConfirmation confirmation = findConfirmation(appointmentId);
        appointment.rejectBooking();
        confirmation.invalidate();
        recordHistory(appointment, AppointmentHistoryAction.REJECTED,
                "confirmationStatus=PENDING", "confirmationStatus=REJECTED");
        return toResponse(appointment);
    }

    @Transactional
    public int expirePendingConfirmations() {
        List<AppointmentConfirmation> expired = appointmentConfirmationRepository
                .findExpiredPending(BookingConfirmationStatus.PENDING,
                        AppointmentStatus.BOOKED, LocalDateTime.now());
        expired.forEach(this::expireConfirmation);
        return expired.size();
    }

    @Transactional
    public AppointmentResponse cancel(Long appointmentId) {
        return cancel(appointmentId, null);
    }

    @Transactional
    public AppointmentResponse cancel(Long appointmentId, CancellationRequest request) {
        Appointment appointment = findAppointment(appointmentId);
        AppointmentStatus oldStatus = appointment.getStatus();
        appointment.cancel(request == null ? null : request.reason(),
                request == null ? null : request.note());
        if (oldStatus != appointment.getStatus()) {
            if (appointment.getConfirmationStatus() == BookingConfirmationStatus.PENDING) {
                appointmentConfirmationRepository.findByAppointmentId(appointmentId)
                        .ifPresent(AppointmentConfirmation::invalidate);
            }
            recordHistory(appointment, AppointmentHistoryAction.CANCELLED,
                    "status=" + oldStatus,
                    "status=" + appointment.getStatus()
                            + ",reason=" + appointment.getCancellationReason()
                            + ",note=" + appointment.getCancellationNote());
        }
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse markArrived(Long appointmentId) {
        Appointment appointment = findAppointment(appointmentId);
        AppointmentStatus oldStatus = appointment.getStatus();
        appointment.arrive();
        recordStatusChange(appointment, oldStatus);
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse complete(Long appointmentId) {
        Appointment appointment = findAppointment(appointmentId);
        AppointmentStatus oldStatus = appointment.getStatus();
        appointment.complete();
        recordStatusChange(appointment, oldStatus);
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse markNoShow(Long appointmentId) {
        Appointment appointment = findAppointment(appointmentId);
        AppointmentStatus oldStatus = appointment.getStatus();
        appointment.markNoShow();
        recordStatusChange(appointment, oldStatus);
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse reschedule(
            Long appointmentId,
            AppointmentRescheduleRequest request
    ) {
        Appointment appointment = findAppointment(appointmentId);
        appointment.requireReschedulable();

        Barber barber = appointment.getBarber();
        BarberServiceOffering serviceOffering = findServiceOffering(request.serviceId());
        validateServiceBelongsToBarber(serviceOffering, barber.getId());
        validateAppointmentTime(barber, serviceOffering, request.time());
        expirePendingConfirmations();

        List<Appointment> existingAppointments =
                appointmentRepository.findByBarberIdAndDate(
                        barber.getId(),
                        request.date()
                );

        if (overlapsAnyActiveAppointment(
                request.time(),
                serviceOffering.getDurationMinutes(),
                existingAppointments,
                appointmentId
        )) {
            throw new AppointmentSlotAlreadyBookedException();
        }

        rejectBlockedTimeOverlap(
                barber.getId(),
                request.date(),
                request.time(),
                serviceOffering.getDurationMinutes()
        );

        String oldValue = scheduleValue(appointment);
        appointment.reschedule(serviceOffering, request.date(), request.time());
        String newValue = scheduleValue(appointment);
        if (!oldValue.equals(newValue)) {
            recordHistory(appointment, AppointmentHistoryAction.RESCHEDULED,
                    oldValue, newValue);
        }
        return toResponse(appointment);
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
    public List<AppointmentResponse> findByCustomer(Long customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        return appointmentRepository.findByCustomerId(customerId).stream()
                .sorted(Comparator.comparing(Appointment::getDate)
                        .thenComparing(Appointment::getTime)
                        .reversed())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<AvailableTimeResponse> findAvailableTimes(
            Long barberId,
            LocalDate date,
            Long serviceId
    ) {
        Barber barber = barberRepository.findById(barberId)
                .orElseThrow(() -> new BarberNotFoundException(barberId));
        BarberServiceOffering serviceOffering = findServiceOffering(serviceId);

        validateServiceBelongsToBarber(serviceOffering, barberId);
        expirePendingConfirmations();

        int durationMinutes = serviceOffering.getDurationMinutes();
        List<LocalTime> candidateStartTimes = generateLegalSlotStartTimes(
                barber,
                durationMinutes
        );
        List<Appointment> existingAppointments =
                appointmentRepository.findByBarberIdAndDate(barberId, date);
        List<BlockedTime> blockedTimes =
                blockedTimeRepository.findByBarberIdAndDate(barberId, date);

        return candidateStartTimes.stream()
                .filter(startTime -> !overlapsAnyActiveAppointment(
                        startTime,
                        durationMinutes,
                        existingAppointments,
                        null
                ))
                .filter(startTime -> !overlapsAnyBlockedTime(
                        startTime,
                        durationMinutes,
                        blockedTimes
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

    private Appointment findAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
    }

    private AppointmentConfirmation findConfirmation(Long appointmentId) {
        return appointmentConfirmationRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new InvalidAppointmentConfirmationException(
                        "Appointment confirmation not found"));
    }

    private void requirePendingConfirmation(Appointment appointment) {
        if (appointment.getConfirmationStatus() != BookingConfirmationStatus.PENDING
                || appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new InvalidAppointmentConfirmationException(
                    "Booking confirmation is not pending");
        }
    }

    private void expireConfirmation(AppointmentConfirmation confirmation) {
        Appointment appointment = confirmation.getAppointment();
        appointment.expireBooking();
        confirmation.invalidate();
        recordHistory(appointment, AppointmentHistoryAction.EXPIRED,
                "confirmationStatus=PENDING", "confirmationStatus=EXPIRED");
    }

    private String newConfirmationCode() {
        return String.format(Locale.ROOT, "%06d", CODE_RANDOM.nextInt(1_000_000));
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

    private boolean overlapsAnyActiveAppointment(
            LocalTime newStart,
            int durationMinutes,
            List<Appointment> existingAppointments,
            Long excludedAppointmentId
    ) {
        LocalTime newEnd = newStart.plusMinutes(durationMinutes);

        return existingAppointments.stream()
                .filter(this::blocksAvailability)
                .filter(existingAppointment -> excludedAppointmentId == null
                        || !Objects.equals(existingAppointment.getId(), excludedAppointmentId))
                .anyMatch(existingAppointment -> {
                    LocalTime existingStart = existingAppointment.getTime();
                    LocalTime existingEnd = existingStart.plusMinutes(
                            existingAppointment.getServiceOffering().getDurationMinutes()
                    );
                    return TimeIntervals.overlap(
                            newStart, newEnd, existingStart, existingEnd
                    );
                });
    }

    private void rejectBlockedTimeOverlap(
            Long barberId,
            LocalDate date,
            LocalTime startTime,
            int durationMinutes
    ) {
        List<BlockedTime> blockedTimes =
                blockedTimeRepository.findByBarberIdAndDate(barberId, date);
        if (overlapsAnyBlockedTime(startTime, durationMinutes, blockedTimes)) {
            throw new AppointmentOverlapsBlockedTimeException();
        }
    }

    private boolean overlapsAnyBlockedTime(
            LocalTime newStart,
            int durationMinutes,
            List<BlockedTime> blockedTimes
    ) {
        LocalTime newEnd = newStart.plusMinutes(durationMinutes);
        return blockedTimes.stream().anyMatch(blockedTime -> TimeIntervals.overlap(
                newStart,
                newEnd,
                blockedTime.getStartTime(),
                blockedTime.getEndTime()
        ));
    }

    private boolean blocksAvailability(Appointment appointment) {
        return appointment.getStatus().isActive()
                && appointment.getConfirmationStatus() != BookingConfirmationStatus.EXPIRED
                && appointment.getConfirmationStatus() != BookingConfirmationStatus.REJECTED;
    }

    private void recordStatusChange(Appointment appointment, AppointmentStatus oldStatus) {
        if (oldStatus != appointment.getStatus()) {
            recordHistory(appointment, AppointmentHistoryAction.STATUS_CHANGED,
                    "status=" + oldStatus, "status=" + appointment.getStatus());
        }
    }

    private void recordHistory(Appointment appointment, AppointmentHistoryAction action,
                               String oldValue, String newValue) {
        appointmentHistoryRepository.save(
                new AppointmentHistory(appointment, action, oldValue, newValue));
    }

    private String creationValue(Appointment appointment) {
        Customer customer = appointment.getCustomer();
        return scheduleValue(appointment)
                + ",barberId=" + appointment.getBarber().getId()
                + ",customerId=" + (customer == null ? null : customer.getId())
                + ",guestName=" + appointment.getGuestName()
                + ",status=" + appointment.getStatus()
                + ",confirmationStatus=" + appointment.getConfirmationStatus();
    }

    private String scheduleValue(Appointment appointment) {
        return "serviceId=" + appointment.getServiceOffering().getId()
                + ",date=" + appointment.getDate()
                + ",time=" + appointment.getTime();
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        BarberServiceOffering serviceOffering = appointment.getServiceOffering();
        Customer customer = appointment.getCustomer();
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
                customer == null ? null : customer.getId(),
                customer == null ? null : customer.getName(),
                customer == null ? null : customer.getPhone(),
                customer == null ? appointment.getGuestName() : null,
                customer == null ? appointment.getGuestPhone() : null,
                appointment.getStatus(),
                appointment.getCancellationReason(),
                appointment.getCancellationNote(),
                appointment.getConfirmationStatus()
        );
    }
}
