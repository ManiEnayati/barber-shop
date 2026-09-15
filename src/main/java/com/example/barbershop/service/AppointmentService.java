package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentRescheduleRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.AvailableTimeResponse;
import com.example.barbershop.dto.CancellationRequest;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentHistory;
import com.example.barbershop.entity.AppointmentHistoryAction;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.AppointmentNotFoundException;
import com.example.barbershop.exception.AppointmentOverlapsBlockedTimeException;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.BarberServiceDoesNotBelongToBarberException;
import com.example.barbershop.exception.BarberServiceOfferingNotFoundException;
import com.example.barbershop.exception.CustomerNotFoundException;
import com.example.barbershop.exception.InvalidAppointmentTimeException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.AppointmentHistoryRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import com.example.barbershop.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class AppointmentService {

    private static final int SLOT_MINUTES = 30;

    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final BarberRepository barberRepository;
    private final BarberServiceOfferingRepository barberServiceOfferingRepository;
    private final BlockedTimeRepository blockedTimeRepository;
    private final CustomerRepository customerRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            BarberRepository barberRepository,
            BarberServiceOfferingRepository barberServiceOfferingRepository,
            BlockedTimeRepository blockedTimeRepository,
            CustomerRepository customerRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
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

        Appointment appointment = customer != null
                ? new Appointment(barber, serviceOffering, customer,
                        request.date(), request.time())
                : new Appointment(barber, serviceOffering, request.guestName(),
                        request.guestPhone(), request.date(), request.time());

        Appointment saved = appointmentRepository.save(appointment);
        recordHistory(saved, AppointmentHistoryAction.CREATED,
                null, creationValue(saved));
        return toResponse(saved);
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
        return appointment.getStatus().isActive();
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
                + ",status=" + appointment.getStatus();
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
                appointment.getCancellationNote()
        );
    }
}
