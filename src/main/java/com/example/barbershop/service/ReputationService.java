package com.example.barbershop.service;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberReputation;
import com.example.barbershop.entity.CancellationReason;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.CustomerReputation;
import com.example.barbershop.entity.ReputationEvent;
import com.example.barbershop.entity.ReputationEventType;
import com.example.barbershop.entity.ReputationSubjectType;
import com.example.barbershop.repository.BarberReputationRepository;
import com.example.barbershop.repository.CustomerReputationRepository;
import com.example.barbershop.repository.ReputationEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
public class ReputationService {

    private final CustomerReputationRepository customerReputationRepository;
    private final BarberReputationRepository barberReputationRepository;
    private final ReputationEventRepository reputationEventRepository;
    private final CustomerIdentityEligibility customerIdentityEligibility;
    private final Clock clock;

    public ReputationService(
            CustomerReputationRepository customerReputationRepository,
            BarberReputationRepository barberReputationRepository,
            ReputationEventRepository reputationEventRepository,
            CustomerIdentityEligibility customerIdentityEligibility,
            Clock clock
    ) {
        this.customerReputationRepository = customerReputationRepository;
        this.barberReputationRepository = barberReputationRepository;
        this.reputationEventRepository = reputationEventRepository;
        this.customerIdentityEligibility = customerIdentityEligibility;
        this.clock = clock;
    }

    @Transactional
    public CustomerReputation getOrCreateCustomerReputation(Customer customer) {
        if (!customerIdentityEligibility.isEligible(customer)) {
            throw new IllegalArgumentException(
                    "Customer reputation requires a verified user identity");
        }
        return customerReputationRepository.findForUpdateByCustomerId(customer.getId())
                .orElseGet(() -> customerReputationRepository.save(
                        new CustomerReputation(customer, clock.instant())));
    }

    @Transactional
    public BarberReputation getOrCreateBarberReputation(Barber barber) {
        return barberReputationRepository.findForUpdateByBarberId(barber.getId())
                .orElseGet(() -> barberReputationRepository.save(
                        new BarberReputation(barber, clock.instant())));
    }

    @Transactional
    public void finalizeOutcome(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            applyCustomerCompleted(appointment);
            applyBarberDelay(appointment);
            applyBarberCompleted(appointment);
            return;
        }
        if (appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            applyBarberDelay(appointment);
            return;
        }
        if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
            return;
        }

        CancellationReason reason = appointment.getCancellationReason();
        if (reason == CancellationReason.BARBER_REQUEST) {
            applyBarberCancellation(appointment);
            return;
        }
        if (reason == CancellationReason.CUSTOMER_LATE) {
            applyCustomerLateCancellation(appointment);
        }
        if (reason == CancellationReason.CUSTOMER_EARLY
                || reason == CancellationReason.CUSTOMER_LATE
                || reason == CancellationReason.BARBER_DELAY) {
            applyBarberDelay(appointment);
        }
    }

    void confirmCustomerNoShow(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.NO_SHOW) {
            throw new IllegalArgumentException(
                    "Customer absence can only be confirmed for a no-show appointment");
        }
        applyCustomerNoShow(appointment);
    }

    int barberDelayPenalty(Integer maxDelayMinutes) {
        if (maxDelayMinutes == null || maxDelayMinutes <= 0) {
            return 0;
        }
        if (maxDelayMinutes <= 15) {
            return 1;
        }
        if (maxDelayMinutes <= 30) {
            return 2;
        }
        return 4;
    }

    int barberCancellationPenalty(
            Appointment appointment,
            LocalDateTime currentTime
    ) {
        LocalDateTime scheduledStart = LocalDateTime.of(
                appointment.getDate(), appointment.getTime());
        if (!currentTime.isAfter(scheduledStart.minusHours(24))) {
            return 2;
        }
        if (!currentTime.isAfter(scheduledStart.minusHours(2))) {
            return 4;
        }
        return 8;
    }

    private void applyCustomerCompleted(Appointment appointment) {
        Customer customer = appointment.getCustomer();
        if (!appointment.isCustomerAccepted()
                || !customerIdentityEligibility.isEligible(customer) || eventExists(
                appointment,
                ReputationSubjectType.CUSTOMER,
                ReputationEventType.CUSTOMER_COMPLETED)) {
            return;
        }
        Instant occurredAt = clock.instant();
        CustomerReputation reputation = getOrCreateCustomerReputation(customer);
        int delta = reputation.recordCompleted(occurredAt);
        saveEvent(appointment, ReputationSubjectType.CUSTOMER,
                ReputationEventType.CUSTOMER_COMPLETED, delta, occurredAt);
    }

    private void applyCustomerLateCancellation(Appointment appointment) {
        Customer customer = appointment.getCustomer();
        if (!appointment.isCustomerAccepted()
                || !customerIdentityEligibility.isEligible(customer) || eventExists(
                appointment,
                ReputationSubjectType.CUSTOMER,
                ReputationEventType.CUSTOMER_LATE_CANCELLATION)) {
            return;
        }
        Instant occurredAt = clock.instant();
        CustomerReputation reputation = getOrCreateCustomerReputation(customer);
        int delta = reputation.recordLateCancellation(occurredAt);
        saveEvent(appointment, ReputationSubjectType.CUSTOMER,
                ReputationEventType.CUSTOMER_LATE_CANCELLATION, delta, occurredAt);
    }

    private void applyCustomerNoShow(Appointment appointment) {
        Customer customer = appointment.getCustomer();
        if (!appointment.isCustomerAccepted()
                || !customerIdentityEligibility.isEligible(customer) || eventExists(
                appointment,
                ReputationSubjectType.CUSTOMER,
                ReputationEventType.CUSTOMER_NO_SHOW)) {
            return;
        }
        Instant occurredAt = clock.instant();
        CustomerReputation reputation = getOrCreateCustomerReputation(customer);
        int delta = reputation.recordNoShow(occurredAt);
        saveEvent(appointment, ReputationSubjectType.CUSTOMER,
                ReputationEventType.CUSTOMER_NO_SHOW, delta, occurredAt);
    }

    private void applyBarberCompleted(Appointment appointment) {
        if (eventExists(appointment, ReputationSubjectType.BARBER,
                ReputationEventType.BARBER_COMPLETED)) {
            return;
        }
        Instant occurredAt = clock.instant();
        BarberReputation reputation = getOrCreateBarberReputation(
                appointment.getBarber());
        int delta = reputation.recordCompleted(occurredAt);
        saveEvent(appointment, ReputationSubjectType.BARBER,
                ReputationEventType.BARBER_COMPLETED, delta, occurredAt);
    }

    private void applyBarberDelay(Appointment appointment) {
        int penalty = barberDelayPenalty(appointment.getMaxBarberDelayMinutes());
        if (penalty == 0 || eventExists(
                appointment,
                ReputationSubjectType.BARBER,
                ReputationEventType.BARBER_DELAY)) {
            return;
        }
        Instant occurredAt = clock.instant();
        BarberReputation reputation = getOrCreateBarberReputation(
                appointment.getBarber());
        int delta = reputation.recordDelay(penalty, occurredAt);
        saveEvent(appointment, ReputationSubjectType.BARBER,
                ReputationEventType.BARBER_DELAY, delta, occurredAt);
    }

    private void applyBarberCancellation(Appointment appointment) {
        if (eventExists(appointment, ReputationSubjectType.BARBER,
                ReputationEventType.BARBER_CANCELLATION)) {
            return;
        }
        Instant occurredAt = clock.instant();
        BarberReputation reputation = getOrCreateBarberReputation(
                appointment.getBarber());
        int penalty = barberCancellationPenalty(
                appointment, LocalDateTime.now(clock));
        int delta = reputation.recordCancellation(penalty, occurredAt);
        saveEvent(appointment, ReputationSubjectType.BARBER,
                ReputationEventType.BARBER_CANCELLATION, delta, occurredAt);
    }

    private boolean eventExists(
            Appointment appointment,
            ReputationSubjectType subjectType,
            ReputationEventType eventType
    ) {
        return reputationEventRepository
                .existsByAppointmentIdAndSubjectTypeAndEventType(
                        appointment.getId(), subjectType, eventType);
    }

    private void saveEvent(
            Appointment appointment,
            ReputationSubjectType subjectType,
            ReputationEventType eventType,
            int delta,
            Instant occurredAt
    ) {
        reputationEventRepository.save(new ReputationEvent(
                appointment, subjectType, eventType, delta, occurredAt));
    }
}
