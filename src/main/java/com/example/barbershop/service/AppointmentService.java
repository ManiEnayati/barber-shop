package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentConfirmRequest;
import com.example.barbershop.dto.BarberAppointmentBookingRequest;
import com.example.barbershop.dto.AppointmentRescheduleRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.AvailableTimeResponse;
import com.example.barbershop.dto.BarberCalendarSlotResponse;
import com.example.barbershop.dto.BarberCalendarSlotStatus;
import com.example.barbershop.dto.CancellationRequest;
import com.example.barbershop.dto.CustomerAppointmentBookingRequest;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentConfirmation;
import com.example.barbershop.entity.AppointmentEventType;
import com.example.barbershop.entity.AppointmentHistory;
import com.example.barbershop.entity.AppointmentHistoryAction;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.BookingSource;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.AppointmentConfirmationAttemptsExceededException;
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
import org.springframework.security.access.AccessDeniedException;

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
    static final int MAX_CONFIRMATION_ATTEMPTS = 5;
    private static final SecureRandom CODE_RANDOM = new SecureRandom();

    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final AppointmentConfirmationRepository appointmentConfirmationRepository;
    private final BarberRepository barberRepository;
    private final BarberServiceOfferingRepository barberServiceOfferingRepository;
    private final BlockedTimeRepository blockedTimeRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentEventService appointmentEventService;
    private final IranianPhoneNormalizer phoneNormalizer;
    private final BarberScheduleService scheduleService;
    private final AppointmentNoShowPolicy noShowPolicy;
    private final ReputationService reputationService;
    private final CustomerIdentityEligibility customerIdentityEligibility;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            AppointmentConfirmationRepository appointmentConfirmationRepository,
            BarberRepository barberRepository,
            BarberServiceOfferingRepository barberServiceOfferingRepository,
            BlockedTimeRepository blockedTimeRepository,
            CustomerRepository customerRepository,
            AppointmentEventService appointmentEventService,
            IranianPhoneNormalizer phoneNormalizer,
            BarberScheduleService scheduleService,
            AppointmentNoShowPolicy noShowPolicy,
            ReputationService reputationService,
            CustomerIdentityEligibility customerIdentityEligibility
    ) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.appointmentConfirmationRepository = appointmentConfirmationRepository;
        this.barberRepository = barberRepository;
        this.barberServiceOfferingRepository = barberServiceOfferingRepository;
        this.blockedTimeRepository = blockedTimeRepository;
        this.customerRepository = customerRepository;
        this.appointmentEventService = appointmentEventService;
        this.phoneNormalizer = phoneNormalizer;
        this.scheduleService = scheduleService;
        this.noShowPolicy = noShowPolicy;
        this.reputationService = reputationService;
        this.customerIdentityEligibility = customerIdentityEligibility;
    }

    @Transactional
    public AppointmentResponse create(AppointmentCreateRequest request) {
        Barber barber = barberRepository.findById(request.barberId())
                .orElseThrow(() -> new BarberNotFoundException(request.barberId()));
        BarberServiceOffering serviceOffering = findServiceOffering(request.serviceId());
        Customer customer = request.customerId() == null ? null : customerRepository
                .findById(request.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(request.customerId()));

        validateNewBooking(barber, serviceOffering, request.date(), request.time());

        BookingSource source = request.source() == null
                ? BookingSource.CUSTOMER : request.source();
        String guestPhone = customer == null
                ? normalizeOptionalGuestPhone(request.guestPhone())
                : null;
        Appointment appointment = customer != null
                ? new Appointment(barber, serviceOffering, customer, source,
                        request.date(), request.time())
                : new Appointment(barber, serviceOffering, request.guestName(),
                        guestPhone, request.date(), request.time());

        return saveCreatedAppointment(appointment);
    }

    @Transactional
    public AppointmentResponse createCustomerBooking(
            Customer customer,
            CustomerAppointmentBookingRequest request
    ) {
        if (!customerIdentityEligibility.isEligible(customer)) {
            throw new AccessDeniedException(
                    "Verified linked customer profile is required");
        }
        Barber barber = barberRepository.findById(request.barberId())
                .orElseThrow(() -> new BarberNotFoundException(request.barberId()));
        BarberServiceOffering serviceOffering = findServiceOffering(request.serviceId());
        validateNewBooking(barber, serviceOffering, request.date(), request.time());
        return saveCreatedAppointment(new Appointment(
                barber, serviceOffering, customer, BookingSource.CUSTOMER,
                request.date(), request.time()));
    }

    @Transactional
    public AppointmentResponse createBarberManualBooking(
            Barber barber,
            BarberAppointmentBookingRequest request
    ) {
        BarberServiceOffering serviceOffering = findServiceOffering(request.serviceId());
        validateNewBooking(barber, serviceOffering, request.date(), request.time());
        return saveCreatedAppointment(Appointment.barberManualGuestBooking(
                barber,
                serviceOffering,
                request.guestName(),
                normalizeOptionalGuestPhone(request.guestPhone()),
                request.date(),
                request.time()));
    }

    private AppointmentResponse saveCreatedAppointment(Appointment appointment) {
        Appointment saved = appointmentRepository.save(appointment);
        appointmentEventService.publish(saved, AppointmentEventType.APPOINTMENT_CREATED);
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

    @Transactional(noRollbackFor = {
            ConfirmationCodeExpiredException.class,
            InvalidAppointmentConfirmationException.class
    })
    public AppointmentResponse confirm(Long appointmentId,
                                       AppointmentConfirmRequest request) {
        Appointment appointment = findAppointment(appointmentId);
        requirePendingConfirmation(appointment);
        AppointmentConfirmation confirmation = findConfirmationForUpdate(appointmentId);
        LocalDateTime now = LocalDateTime.now();
        if (confirmation.isExpired(now)) {
            expireConfirmation(confirmation);
            throw new ConfirmationCodeExpiredException();
        }
        if (confirmation.hasReachedFailedAttemptLimit(
                MAX_CONFIRMATION_ATTEMPTS)) {
            throw new AppointmentConfirmationAttemptsExceededException();
        }
        if (!confirmation.matchesCode(request.code())) {
            confirmation.recordFailedAttempt();
            throw new InvalidAppointmentConfirmationException("Confirmation code is incorrect");
        }
        confirmation.markConfirmed(now);
        appointment.confirmBooking();
        appointmentEventService.publish(
                appointment,
                AppointmentEventType.APPOINTMENT_CONFIRMED
        );
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
            appointmentEventService.publish(
                    appointment,
                    AppointmentEventType.APPOINTMENT_CANCELLED
            );
            reputationService.finalizeOutcome(appointment);
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
        reputationService.finalizeOutcome(appointment);
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse markNoShow(Long appointmentId) {
        Appointment appointment = findAppointment(appointmentId);
        AppointmentStatus oldStatus = appointment.getStatus();
        noShowPolicy.markNoShow(appointment, LocalDateTime.now());
        recordStatusChange(appointment, oldStatus);
        reputationService.finalizeOutcome(appointment);
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
        validateRescheduleSlot(appointment, serviceOffering,
                request.date(), request.time());

        String oldValue = scheduleValue(appointment);
        appointment.reschedule(serviceOffering, request.date(), request.time());
        String newValue = scheduleValue(appointment);
        if (!oldValue.equals(newValue)) {
            recordHistory(appointment, AppointmentHistoryAction.RESCHEDULED,
                    oldValue, newValue);
            appointmentEventService.publish(
                    appointment,
                    AppointmentEventType.APPOINTMENT_RESCHEDULED
            );
        }
        return toResponse(appointment);
    }

    void validateCustomerRescheduleSlot(
            Appointment appointment,
            LocalDate date,
            LocalTime time
    ) {
        validateRescheduleSlot(appointment, appointment.getServiceOffering(), date, time);
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
    public List<AppointmentResponse> findByBarber(
            Long barberId,
            LocalDate date,
            AppointmentStatus status
    ) {
        barberRepository.findById(barberId)
                .orElseThrow(() -> new BarberNotFoundException(barberId));

        List<Appointment> appointments = date == null
                ? appointmentRepository.findByBarberId(barberId)
                : appointmentRepository.findByBarberIdAndDate(barberId, date);

        return appointments.stream()
                .filter(appointment -> status == null
                        || appointment.getStatus() == status)
                .sorted(Comparator.comparing(Appointment::getDate)
                        .thenComparing(Appointment::getTime))
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
                date,
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

    @Transactional
    public List<BarberCalendarSlotResponse> findCalendarSlots(
            Barber barber,
            LocalDate date
    ) {
        expirePendingConfirmations();
        List<Appointment> appointments = appointmentRepository
                .findByBarberIdAndDate(barber.getId(), date);
        List<BlockedTime> blockedTimes = blockedTimeRepository
                .findByBarberIdAndDate(barber.getId(), date);

        return generateLegalSlotStartTimes(barber, date, SLOT_MINUTES).stream()
                .map(time -> toCalendarSlot(time, appointments, blockedTimes))
                .toList();
    }

    private BarberCalendarSlotResponse toCalendarSlot(
            LocalTime time,
            List<Appointment> appointments,
            List<BlockedTime> blockedTimes
    ) {
        Appointment appointment = appointments.stream()
                .filter(this::blocksAvailability)
                .filter(existing -> TimeIntervals.overlap(
                        time,
                        time.plusMinutes(SLOT_MINUTES),
                        existing.getTime(),
                        existing.getTime().plusMinutes(
                                existing.getServiceOffering().getDurationMinutes()
                        )
                ))
                .findFirst()
                .orElse(null);
        if (appointment != null) {
            return new BarberCalendarSlotResponse(
                    time,
                    BarberCalendarSlotStatus.BOOKED,
                    appointment.getId()
            );
        }

        boolean blocked = blockedTimes.stream().anyMatch(blockedTime ->
                TimeIntervals.overlap(
                        time,
                        time.plusMinutes(SLOT_MINUTES),
                        blockedTime.getStartTime(),
                        blockedTime.getEndTime()
                ));
        return new BarberCalendarSlotResponse(
                time,
                blocked ? BarberCalendarSlotStatus.BLOCKED
                        : BarberCalendarSlotStatus.AVAILABLE,
                null
        );
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

    private AppointmentConfirmation findConfirmationForUpdate(Long appointmentId) {
        return appointmentConfirmationRepository
                .findByAppointmentIdForUpdate(appointmentId)
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
            LocalDate date,
            LocalTime time
    ) {
        if (!generateLegalSlotStartTimes(
                barber,
                date,
                serviceOffering.getDurationMinutes()
        ).contains(time)) {
            throw new InvalidAppointmentTimeException();
        }
    }

    private List<LocalTime> generateLegalSlotStartTimes(
            Barber barber,
            LocalDate date,
            int durationMinutes
    ) {
        List<LocalTime> startTimes = new ArrayList<>();
        var hours = scheduleService.workingHours(barber, date);
        if (hours.isEmpty()) {
            return startTimes;
        }
        LocalTime currentTime = hours.get().start();

        while (Duration.between(currentTime, hours.get().end()).toMinutes()
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
        BookingConfirmationStatus confirmationStatus = appointment.getConfirmationStatus();
        return appointment.getStatus().isActive()
                && (confirmationStatus == null || confirmationStatus.isActive());
    }

    private void validateNewBooking(
            Barber barber,
            BarberServiceOffering serviceOffering,
            LocalDate date,
            LocalTime time
    ) {
        validateServiceBelongsToBarber(serviceOffering, barber.getId());
        validateAppointmentTime(barber, serviceOffering, date, time);
        expirePendingConfirmations();

        List<Appointment> existingAppointments = appointmentRepository
                .findByBarberIdAndDate(barber.getId(), date);
        if (overlapsAnyActiveAppointment(
                time,
                serviceOffering.getDurationMinutes(),
                existingAppointments,
                null
        )) {
            throw new AppointmentSlotAlreadyBookedException();
        }
        rejectBlockedTimeOverlap(
                barber.getId(), date, time, serviceOffering.getDurationMinutes());
    }

    private void validateRescheduleSlot(
            Appointment appointment,
            BarberServiceOffering serviceOffering,
            LocalDate date,
            LocalTime time
    ) {
        Barber barber = appointment.getBarber();
        validateAppointmentTime(barber, serviceOffering, date, time);
        expirePendingConfirmations();

        List<Appointment> existingAppointments =
                appointmentRepository.findByBarberIdAndDate(barber.getId(), date);
        if (overlapsAnyActiveAppointment(
                time,
                serviceOffering.getDurationMinutes(),
                existingAppointments,
                appointment.getId()
        )) {
            throw new AppointmentSlotAlreadyBookedException();
        }
        rejectBlockedTimeOverlap(
                barber.getId(),
                date,
                time,
                serviceOffering.getDurationMinutes()
        );
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
                + ",confirmationStatus=" + appointment.getConfirmationStatus()
                + ",bookingSource=" + appointment.getBookingSource()
                + ",customerAccepted=" + appointment.isCustomerAccepted();
    }

    private String scheduleValue(Appointment appointment) {
        return "serviceId=" + appointment.getServiceOffering().getId()
                + ",date=" + appointment.getDate()
                + ",time=" + appointment.getTime();
    }

    private String normalizeOptionalGuestPhone(String guestPhone) {
        return guestPhone == null || guestPhone.isBlank()
                ? null
                : phoneNormalizer.normalize(guestPhone);
    }

    AppointmentResponse toResponse(Appointment appointment) {
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
                appointment.getConfirmationStatus(),
                appointment.getExpectedArrivalTime() == null
                        ? null : appointment.getDelayMinutes(),
                appointment.getExpectedArrivalTime(),
                appointment.getBookingSource(),
                appointment.isCustomerAccepted()
        );
    }
}
