package com.example.barbershop.service;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberReputation;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.CancellationReason;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.CustomerReputation;
import com.example.barbershop.entity.ReputationEvent;
import com.example.barbershop.entity.ReputationEventType;
import com.example.barbershop.entity.ReputationSubjectType;
import com.example.barbershop.entity.User;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberReputationRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.repository.CustomerReputationRepository;
import com.example.barbershop.repository.ReputationEventRepository;
import com.example.barbershop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ReputationServiceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(
            NOW, ZoneOffset.UTC);

    @Autowired private ReputationService reputationService;
    @Autowired private CustomerReputationRepository customerReputationRepository;
    @Autowired private BarberReputationRepository barberReputationRepository;
    @Autowired private ReputationEventRepository eventRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private Clock clock;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void newProfilesStartAtOneHundredWithZeroCounters() {
        Appointment appointment = appointmentAt(LOCAL_NOW.plusDays(2), true);

        CustomerReputation customer = reputationService
                .getOrCreateCustomerReputation(appointment.getCustomer());
        BarberReputation barber = reputationService
                .getOrCreateBarberReputation(appointment.getBarber());

        assertEquals(100, customer.getScore());
        assertEquals(0, customer.getCompletedCount());
        assertEquals(0, customer.getLateCancellationCount());
        assertEquals(0, customer.getNoShowCount());
        assertEquals(100, barber.getScore());
        assertEquals(0, barber.getCompletedCount());
        assertEquals(0, barber.getDelayCount());
        assertEquals(0, barber.getCancellationCount());
    }

    @Test
    void completedOutcomeIsCappedAndIdempotentForBothSubjects() {
        Appointment appointment = appointmentAt(LOCAL_NOW.plusDays(2), true);
        appointment.arrive();
        appointment.complete();

        reputationService.finalizeOutcome(appointment);
        reputationService.finalizeOutcome(appointment);

        CustomerReputation customer = customerReputation(appointment);
        BarberReputation barber = barberReputation(appointment);
        assertEquals(100, customer.getScore());
        assertEquals(1, customer.getCompletedCount());
        assertEquals(100, barber.getScore());
        assertEquals(1, barber.getCompletedCount());
        List<ReputationEvent> events = events(appointment);
        assertEquals(2, events.size());
        assertEquals(List.of(
                        ReputationEventType.CUSTOMER_COMPLETED,
                        ReputationEventType.BARBER_COMPLETED),
                events.stream().map(ReputationEvent::getEventType).toList());
        assertEquals(List.of(0, 0),
                events.stream().map(ReputationEvent::getDelta).toList());
    }

    @Test
    void completionRecoversOnePointAfterCustomerLateCancellation() {
        Appointment late = appointmentAt(LOCAL_NOW.plusDays(2), true);
        late.cancelByCustomer(CancellationReason.CUSTOMER_LATE);
        reputationService.finalizeOutcome(late);

        Appointment completed = appointmentAt(LOCAL_NOW.plusDays(3), true,
                late.getBarber(), late.getCustomer());
        completed.arrive();
        completed.complete();
        reputationService.finalizeOutcome(completed);

        CustomerReputation reputation = customerReputation(late);
        assertEquals(98, reputation.getScore());
        assertEquals(1, reputation.getLateCancellationCount());
        assertEquals(1, reputation.getCompletedCount());
        assertEquals(1, events(completed).stream()
                .filter(event -> event.getSubjectType()
                        == ReputationSubjectType.CUSTOMER)
                .findFirst().orElseThrow().getDelta());
    }

    @Test
    void earlyAndBarberDelayCancellationsDoNotPenalizeCustomer() {
        Appointment early = appointmentAt(LOCAL_NOW.plusDays(2), true);
        CustomerReputation reputation = reputationService
                .getOrCreateCustomerReputation(early.getCustomer());
        early.cancelByCustomer(CancellationReason.CUSTOMER_EARLY);
        reputationService.finalizeOutcome(early);

        Appointment delayed = appointmentAt(LOCAL_NOW.plusDays(3), true,
                early.getBarber(), early.getCustomer());
        delayed.updateDelay(10);
        delayed.cancelByCustomer(CancellationReason.BARBER_DELAY);
        reputationService.finalizeOutcome(delayed);

        assertEquals(100, reputation.getScore());
        assertEquals(0, reputation.getLateCancellationCount());
        assertEquals(0, reputation.getNoShowCount());
        assertTrue(eventRepository.findAll().stream().noneMatch(event ->
                event.getSubjectType() == ReputationSubjectType.CUSTOMER));
    }

    @Test
    void lateCancellationAndNoShowApplyCustomerPenaltiesOnce() {
        Appointment late = appointmentAt(LOCAL_NOW.plusDays(2), true);
        late.cancelByCustomer(CancellationReason.CUSTOMER_LATE);
        reputationService.finalizeOutcome(late);
        reputationService.finalizeOutcome(late);

        Appointment noShow = appointmentAt(LOCAL_NOW.plusDays(3), true,
                late.getBarber(), late.getCustomer());
        noShow.markNoShow();
        reputationService.finalizeOutcome(noShow);
        reputationService.finalizeOutcome(noShow);
        reputationService.confirmCustomerNoShow(noShow);
        reputationService.confirmCustomerNoShow(noShow);

        CustomerReputation reputation = customerReputation(late);
        assertEquals(89, reputation.getScore());
        assertEquals(1, reputation.getLateCancellationCount());
        assertEquals(1, reputation.getNoShowCount());
        assertEquals(-3, events(late).getFirst().getDelta());
        assertEquals(-8, events(noShow).getFirst().getDelta());
    }

    @Test
    void scoreNeverFallsBelowZeroAndEventStoresActualClampedDelta() {
        Appointment first = appointmentAt(LOCAL_NOW.plusDays(2), true);
        Customer customer = first.getCustomer();
        Barber barber = first.getBarber();
        first.markNoShow();
        reputationService.finalizeOutcome(first);
        reputationService.confirmCustomerNoShow(first);

        Appointment last = first;
        for (int index = 1; index < 14; index++) {
            last = appointmentAt(
                    LOCAL_NOW.plusDays(index + 2), true, barber, customer);
            last.markNoShow();
            reputationService.finalizeOutcome(last);
            reputationService.confirmCustomerNoShow(last);
        }

        CustomerReputation reputation = customerReputation(first);
        assertEquals(0, reputation.getScore());
        assertEquals(14, reputation.getNoShowCount());
        assertEquals(0, events(last).stream()
                .filter(event -> event.getSubjectType()
                        == ReputationSubjectType.CUSTOMER)
                .findFirst().orElseThrow().getDelta());
    }

    @Test
    void reschedulesNeverChangeCustomerReputation() {
        Appointment appointment = appointmentAt(LOCAL_NOW.plusDays(2), true);
        CustomerReputation reputation = reputationService
                .getOrCreateCustomerReputation(appointment.getCustomer());

        appointment.rescheduleByCustomer(
                appointment.getDate().plusDays(1), appointment.getTime());
        appointment.updateDelay(20);
        appointment.rescheduleByCustomer(
                appointment.getDate().plusDays(1), appointment.getTime());

        assertEquals(100, reputation.getScore());
        assertEquals(0, reputation.getCompletedCount());
        assertTrue(eventRepository.findAll().isEmpty());
    }

    @Test
    void delayBandsApplyOnePenaltyAtTerminalOutcome() {
        assertDelayPenalty(15, 99, -1);
        assertDelayPenalty(16, 98, -2);
        assertDelayPenalty(31, 96, -4);
    }

    @Test
    void maximumDelaySurvivesDownwardUpdateRemovalAndCustomerReschedule() {
        Appointment appointment = appointmentAt(LOCAL_NOW.plusDays(2), true);
        appointment.updateDelay(10);
        appointment.updateDelay(20);
        appointment.updateDelay(40);
        appointment.updateDelay(10);
        appointment.removeDelay();

        assertNull(appointment.getDelayMinutes());
        assertNull(appointment.getExpectedArrivalTime());
        assertEquals(40, appointment.getMaxBarberDelayMinutes());

        appointment.rescheduleByCustomer(
                appointment.getDate().plusDays(1), appointment.getTime());
        assertEquals(40, appointment.getMaxBarberDelayMinutes());
        appointment.arrive();
        appointment.complete();
        reputationService.finalizeOutcome(appointment);
        reputationService.finalizeOutcome(appointment);

        BarberReputation reputation = barberReputation(appointment);
        assertEquals(97, reputation.getScore());
        assertEquals(1, reputation.getDelayCount());
        assertEquals(1, reputation.getCompletedCount());
        assertEquals(List.of(
                        ReputationEventType.CUSTOMER_COMPLETED,
                        ReputationEventType.BARBER_DELAY,
                        ReputationEventType.BARBER_COMPLETED),
                events(appointment).stream()
                        .map(ReputationEvent::getEventType).toList());
        assertEquals(List.of(0, -4, 1), events(appointment).stream()
                .map(ReputationEvent::getDelta).toList());
    }

    @Test
    void lateCancellationAndNoShowAlsoApplyHistoricalBarberDelay() {
        Appointment late = appointmentAt(LOCAL_NOW.plusDays(2), true);
        late.updateDelay(40);
        late.rescheduleByCustomer(
                late.getDate().plusDays(1), late.getTime());
        late.cancelByCustomer(CancellationReason.CUSTOMER_LATE);
        reputationService.finalizeOutcome(late);

        assertEquals(97, customerReputation(late).getScore());
        assertEquals(96, barberReputation(late).getScore());
        assertEquals(1, barberReputation(late).getDelayCount());

        Appointment noShow = appointmentAt(LOCAL_NOW.plusDays(4), true,
                late.getBarber(), late.getCustomer());
        noShow.updateDelay(20);
        noShow.markNoShow();
        reputationService.finalizeOutcome(noShow);
        reputationService.confirmCustomerNoShow(noShow);

        assertEquals(89, customerReputation(late).getScore());
        assertEquals(94, barberReputation(late).getScore());
        assertEquals(2, barberReputation(late).getDelayCount());
    }

    @Test
    void barberCancellationSuppressesHistoricalDelayPenalty() {
        Appointment appointment = appointmentAt(LOCAL_NOW.plusHours(1), true);
        appointment.updateDelay(40);
        appointment.cancelByBarber("Emergency");

        reputationService.finalizeOutcome(appointment);
        reputationService.finalizeOutcome(appointment);

        BarberReputation reputation = barberReputation(appointment);
        assertEquals(92, reputation.getScore());
        assertEquals(1, reputation.getCancellationCount());
        assertEquals(0, reputation.getDelayCount());
        assertEquals(List.of(ReputationEventType.BARBER_CANCELLATION),
                events(appointment).stream()
                        .map(ReputationEvent::getEventType).toList());
    }

    @Test
    void barberCancellationPenaltyUsesExactCurrentScheduledStartBoundaries() {
        Appointment appointment = appointmentAt(LOCAL_NOW.plusDays(2), true);

        assertEquals(2, reputationService.barberCancellationPenalty(
                appointment, scheduledStart(appointment).minusHours(30)));
        assertEquals(2, reputationService.barberCancellationPenalty(
                appointment, scheduledStart(appointment).minusHours(24)));
        assertEquals(4, reputationService.barberCancellationPenalty(
                appointment, scheduledStart(appointment).minusHours(12)));
        assertEquals(4, reputationService.barberCancellationPenalty(
                appointment, scheduledStart(appointment).minusHours(2)));
        assertEquals(8, reputationService.barberCancellationPenalty(
                appointment, scheduledStart(appointment).minusHours(2)
                        .plusSeconds(1)));
        assertEquals(8, reputationService.barberCancellationPenalty(
                appointment, scheduledStart(appointment).plusMinutes(1)));
    }

    @Test
    void guestOutcomeNeverCreatesCustomerReputationOrCustomerEvent() {
        Appointment guest = appointmentAt(LOCAL_NOW.plusDays(2), false);
        guest.arrive();
        guest.complete();

        reputationService.finalizeOutcome(guest);

        Appointment delayedNoShow = appointmentAt(
                LOCAL_NOW.plusDays(3), false, guest.getBarber(), null);
        delayedNoShow.updateDelay(20);
        delayedNoShow.markNoShow();
        reputationService.finalizeOutcome(delayedNoShow);

        assertTrue(customerReputationRepository.findAll().isEmpty());
        assertFalse(barberReputationRepository.findAll().isEmpty());
        assertTrue(events(guest).stream().noneMatch(event ->
                event.getSubjectType() == ReputationSubjectType.CUSTOMER));
        assertTrue(events(delayedNoShow).stream().noneMatch(event ->
                event.getSubjectType() == ReputationSubjectType.CUSTOMER));
        assertEquals(1, barberReputation(guest).getCompletedCount());
        assertEquals(1, barberReputation(guest).getDelayCount());
    }

    @Test
    void unacceptedBarberManualBookingCannotPenalizeVerifiedCustomer() {
        Appointment seed = appointmentAt(LOCAL_NOW.plusDays(1), true);
        Appointment noShow = appointmentRepository.saveAndFlush(
                Appointment.barberManualBooking(
                        seed.getBarber(), seed.getServiceOffering(), seed.getCustomer(),
                        LOCAL_NOW.plusDays(2).toLocalDate(), LocalTime.of(10, 0)));
        noShow.updateDelay(20);
        noShow.markNoShow();
        reputationService.finalizeOutcome(noShow);

        Appointment late = appointmentRepository.saveAndFlush(
                Appointment.barberManualBooking(
                        seed.getBarber(), seed.getServiceOffering(), seed.getCustomer(),
                        LOCAL_NOW.plusDays(3).toLocalDate(), LocalTime.of(11, 0)));
        late.cancelByCustomer(CancellationReason.CUSTOMER_LATE);
        reputationService.finalizeOutcome(late);

        assertFalse(noShow.isCustomerAccepted());
        assertFalse(late.isCustomerAccepted());
        assertTrue(customerReputationRepository.findByCustomerId(
                seed.getCustomer().getId()).isEmpty());
        assertTrue(events(noShow).stream().noneMatch(event ->
                event.getSubjectType() == ReputationSubjectType.CUSTOMER));
        assertTrue(events(late).stream().noneMatch(event ->
                event.getSubjectType() == ReputationSubjectType.CUSTOMER));
        assertEquals(98, barberReputation(noShow).getScore());
        assertEquals(1, barberReputation(noShow).getDelayCount());
    }

    @Test
    void unlinkedCustomerOutcomesNeverCreateCustomerReputationButStillScoreBarber() {
        Appointment seed = appointmentAt(LOCAL_NOW.plusDays(1), false);
        Customer unlinked = customerRepository.save(new Customer(
                "Unlinked", "09125550120"));
        Appointment completed = appointmentAt(LOCAL_NOW.plusDays(2), true,
                seed.getBarber(), unlinked);
        completed.arrive();
        completed.complete();
        reputationService.finalizeOutcome(completed);

        Appointment noShow = appointmentAt(LOCAL_NOW.plusDays(3), true,
                completed.getBarber(), unlinked);
        noShow.updateDelay(20);
        noShow.markNoShow();
        reputationService.finalizeOutcome(noShow);

        Appointment late = appointmentAt(LOCAL_NOW.plusDays(4), true,
                completed.getBarber(), unlinked);
        late.updateDelay(10);
        late.cancelByCustomer(CancellationReason.CUSTOMER_LATE);
        reputationService.finalizeOutcome(late);

        assertTrue(customerReputationRepository.findByCustomerId(
                unlinked.getId()).isEmpty());
        assertTrue(eventRepository.findAll().stream().noneMatch(event ->
                event.getSubjectType() == ReputationSubjectType.CUSTOMER));
        BarberReputation barber = barberReputation(completed);
        assertEquals(1, barber.getCompletedCount());
        assertEquals(2, barber.getDelayCount());
        assertEquals(97, barber.getScore());
    }

    @Test
    void unverifiedLinkedCustomerIsIneligibleWhileBarberStillScores() {
        Appointment seed = appointmentAt(LOCAL_NOW.plusDays(2), false);
        User unverified = userRepository.save(new User("09125550123"));
        Customer customer = new Customer("Unverified", unverified.getPhone());
        ReflectionTestUtils.setField(customer, "user", unverified);
        customerRepository.save(customer);
        Appointment appointment = appointmentAt(LOCAL_NOW.plusDays(3), true,
                seed.getBarber(), customer);
        appointment.updateDelay(10);
        appointment.markNoShow();

        reputationService.finalizeOutcome(appointment);

        assertTrue(customerReputationRepository.findByCustomerId(
                customer.getId()).isEmpty());
        assertTrue(events(appointment).stream().noneMatch(event ->
                event.getSubjectType() == ReputationSubjectType.CUSTOMER));
        assertEquals(99, barberReputation(appointment).getScore());
        assertEquals(1, barberReputation(appointment).getDelayCount());
    }

    @Test
    void matchingPhoneDoesNotTurnAnUnlinkedCustomerIntoAReputationIdentity() {
        Appointment seed = appointmentAt(LOCAL_NOW.plusDays(1), false);
        Customer unlinked = customerRepository.save(new Customer(
                "Unlinked", "09125550124"));
        Appointment appointment = appointmentAt(LOCAL_NOW.plusDays(2), true,
                seed.getBarber(), unlinked);
        User samePhoneUser = new User(unlinked.getPhone());
        samePhoneUser.verifyPhone();
        userRepository.save(samePhoneUser);
        appointment.arrive();
        appointment.complete();

        reputationService.finalizeOutcome(appointment);

        assertTrue(customerReputationRepository.findByCustomerId(
                unlinked.getId()).isEmpty());
        assertTrue(events(appointment).stream().noneMatch(event ->
                event.getSubjectType() == ReputationSubjectType.CUSTOMER));
        assertEquals(1, barberReputation(appointment).getCompletedCount());
    }

    private void assertDelayPenalty(int minutes, int expectedScore, int expectedDelta) {
        Appointment appointment = appointmentAt(
                LOCAL_NOW.plusDays(minutes), true);
        appointment.updateDelay(minutes);
        appointment.cancelByCustomer(CancellationReason.BARBER_DELAY);
        reputationService.finalizeOutcome(appointment);

        BarberReputation reputation = barberReputation(appointment);
        assertEquals(expectedScore, reputation.getScore());
        assertEquals(1, reputation.getDelayCount());
        ReputationEvent event = events(appointment).stream()
                .filter(candidate -> candidate.getSubjectType()
                        == ReputationSubjectType.BARBER)
                .findFirst().orElseThrow();
        assertEquals(ReputationEventType.BARBER_DELAY, event.getEventType());
        assertEquals(expectedDelta, event.getDelta());
    }

    private CustomerReputation customerReputation(Appointment appointment) {
        return customerReputationRepository
                .findByCustomerId(appointment.getCustomer().getId()).orElseThrow();
    }

    private BarberReputation barberReputation(Appointment appointment) {
        return barberReputationRepository
                .findByBarberId(appointment.getBarber().getId()).orElseThrow();
    }

    private List<ReputationEvent> events(Appointment appointment) {
        return eventRepository.findByAppointmentIdOrderByIdAsc(appointment.getId());
    }

    private LocalDateTime scheduledStart(Appointment appointment) {
        return LocalDateTime.of(appointment.getDate(), appointment.getTime());
    }

    private Appointment appointmentAt(LocalDateTime start, boolean registered) {
        Barber barber = barberRepository.save(new Barber(
                "Barber " + start,
                "+98910" + Math.abs(start.hashCode()),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0)));
        String phone = "0912" + String.format("%07d",
                Math.floorMod(start.hashCode(), 10_000_000));
        User user = registered ? new User(phone) : null;
        if (user != null) {
            user.verifyPhone();
            userRepository.save(user);
        }
        Customer customer = registered
                ? customerRepository.save(new Customer(
                        user, "Customer " + start))
                : null;
        return appointmentAt(start, registered, barber, customer);
    }

    private Appointment appointmentAt(
            LocalDateTime start,
            boolean registered,
            Barber barber,
            Customer customer
    ) {
        BarberServiceOffering offering = serviceRepository.save(
                new BarberServiceOffering(
                        barber,
                        "Service " + start,
                        30,
                        400000L));
        Appointment appointment = registered
                ? new Appointment(barber, offering, customer,
                        start.toLocalDate(), start.toLocalTime())
                : new Appointment(barber, offering, "Guest", "+989121234567",
                        start.toLocalDate(), start.toLocalTime());
        return appointmentRepository.saveAndFlush(appointment);
    }
}
