package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentClaim;
import com.example.barbershop.entity.AppointmentClaimStatus;
import com.example.barbershop.entity.AppointmentHistory;
import com.example.barbershop.entity.AppointmentHistoryAction;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
import com.example.barbershop.exception.AppointmentNotFoundException;
import com.example.barbershop.exception.InvalidAppointmentClaimException;
import com.example.barbershop.exception.InvalidIranianPhoneException;
import com.example.barbershop.repository.AppointmentClaimRepository;
import com.example.barbershop.repository.AppointmentHistoryRepository;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppointmentClaimService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentClaimRepository appointmentClaimRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final CustomerRepository customerRepository;
    private final IranianPhoneNormalizer phoneNormalizer;
    private final MeService meService;

    public AppointmentClaimService(
            AppointmentRepository appointmentRepository,
            AppointmentClaimRepository appointmentClaimRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            CustomerRepository customerRepository,
            IranianPhoneNormalizer phoneNormalizer,
            MeService meService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentClaimRepository = appointmentClaimRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.customerRepository = customerRepository;
        this.phoneNormalizer = phoneNormalizer;
        this.meService = meService;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> findClaimableAppointments(Long userId) {
        User user = meService.requireCustomerUser(userId);
        Set<Long> decidedAppointmentIds = appointmentClaimRepository
                .findByUserId(userId)
                .stream()
                .map(claim -> claim.getAppointment().getId())
                .collect(Collectors.toSet());

        return appointmentRepository
                .findByCustomerIsNullAndGuestPhoneIsNotNull()
                .stream()
                .filter(appointment -> !decidedAppointmentIds.contains(
                        appointment.getId()
                ))
                .filter(appointment -> phoneMatches(user, appointment))
                .sorted(Comparator.comparing(Appointment::getDate)
                        .thenComparing(Appointment::getTime)
                        .reversed())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void confirm(Long userId, Long appointmentId) {
        User user = meService.requireCustomerUser(userId);
        rejectPreviousDecision(userId, appointmentId);
        Appointment appointment = requireClaimableAppointment(user, appointmentId);
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new InvalidAppointmentClaimException(
                        "Customer profile is required to confirm a claim"
                ));
        String originalGuestName = appointment.getGuestName();
        String originalGuestPhone = appointment.getGuestPhone();

        appointmentClaimRepository.save(new AppointmentClaim(
                appointment,
                user,
                AppointmentClaimStatus.CONFIRMED
        ));
        appointment.claimBy(customer);
        appointmentHistoryRepository.save(new AppointmentHistory(
                appointment,
                AppointmentHistoryAction.CUSTOMER_CLAIMED,
                "guestName=" + originalGuestName + ",guestPhone=" + originalGuestPhone,
                "customerId=" + customer.getId()
        ));
    }

    @Transactional
    public void reject(Long userId, Long appointmentId) {
        User user = meService.requireCustomerUser(userId);
        rejectPreviousDecision(userId, appointmentId);
        Appointment appointment = requireClaimableAppointment(user, appointmentId);
        appointmentClaimRepository.save(new AppointmentClaim(
                appointment,
                user,
                AppointmentClaimStatus.REJECTED
        ));
    }

    private Appointment requireClaimableAppointment(User user, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        if (appointment.getCustomer() != null || appointment.getGuestPhone() == null
                || !phoneMatches(user, appointment)) {
            throw new InvalidAppointmentClaimException(
                    "Appointment is not claimable by the authenticated user"
            );
        }
        return appointment;
    }

    private void rejectPreviousDecision(Long userId, Long appointmentId) {
        if (appointmentClaimRepository.existsByAppointmentIdAndUserId(
                appointmentId,
                userId
        )) {
            throw new InvalidAppointmentClaimException(
                    "Appointment claim has already been decided"
            );
        }
    }

    private boolean phoneMatches(User user, Appointment appointment) {
        try {
            return user.getPhone().equals(
                    phoneNormalizer.normalize(appointment.getGuestPhone())
            );
        } catch (InvalidIranianPhoneException exception) {
            return false;
        }
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
