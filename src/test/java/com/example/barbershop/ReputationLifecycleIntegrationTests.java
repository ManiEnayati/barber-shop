package com.example.barbershop;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.BarberAppointmentBookingRequest;
import com.example.barbershop.dto.CustomerAppointmentBookingRequest;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberReputation;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.CancellationReason;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.CustomerReputation;
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
import com.example.barbershop.service.AppointmentService;
import com.example.barbershop.service.BarberAppointmentManagementService;
import com.example.barbershop.service.CustomerAppointmentManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ReputationLifecycleIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(
            NOW, ZoneOffset.UTC);

    @Autowired private AppointmentService appointmentService;
    @Autowired private BarberAppointmentManagementService barberManagementService;
    @Autowired private CustomerAppointmentManagementService customerManagementService;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private CustomerReputationRepository customerReputationRepository;
    @Autowired private BarberReputationRepository barberReputationRepository;
    @Autowired private ReputationEventRepository eventRepository;

    @MockitoBean private Clock clock;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void authenticatedBarberCompletionFinalizesReputation() {
        TestActors actors = actors("101");
        Appointment appointment = appointment(
                actors, LOCAL_NOW.minusDays(1), true);
        appointment.arrive();

        barberManagementService.complete(
                actors.barber().getUser().getId(), appointment.getId());

        assertEquals(1, customerReputation(actors).getCompletedCount());
        assertEquals(1, barberReputation(actors).getCompletedCount());
        assertEquals(List.of(
                        ReputationEventType.CUSTOMER_COMPLETED,
                        ReputationEventType.BARBER_COMPLETED),
                eventRepository.findByAppointmentIdOrderByIdAsc(appointment.getId())
                        .stream().map(event -> event.getEventType()).toList());
    }

    @Test
    void authenticatedBarberNoShowWaitsForCustomerConfirmation() {
        TestActors actors = actors("201");
        Appointment appointment = appointment(
                actors, LOCAL_NOW.minusDays(10), true);

        barberManagementService.markNoShow(
                actors.barber().getUser().getId(), appointment.getId());

        assertEquals(0, customerReputationRepository.count());
        assertEquals(0, eventRepository.findByAppointmentIdOrderByIdAsc(
                appointment.getId()).stream().filter(event -> event.getSubjectType()
                == ReputationSubjectType.CUSTOMER).count());

        customerManagementService.respondToNoShow(
                actors.customer().getUser().getId(),
                appointment.getId(),
                com.example.barbershop.entity.NoShowCustomerResponse.CONFIRM_ABSENCE);

        assertEquals(92, customerReputation(actors).getScore());
        assertEquals(1, customerReputation(actors).getNoShowCount());
    }

    @Test
    void customerCancellationFinalizesLatePenalty() {
        TestActors actors = actors("301");
        Appointment appointment = appointment(
                actors, LOCAL_NOW.plusHours(1), true);

        customerManagementService.cancel(
                actors.customer().getUser().getId(), appointment.getId());

        assertEquals(CancellationReason.CUSTOMER_LATE,
                appointment.getCancellationReason());
        assertEquals(97, customerReputation(actors).getScore());
        assertEquals(1, customerReputation(actors).getLateCancellationCount());
    }

    @Test
    void barberCancellationFinalizesOnlyCancellationPenaltyAfterDelay() {
        TestActors actors = actors("401");
        Appointment appointment = appointment(
                actors, LOCAL_NOW.plusHours(1), true);
        appointment.updateDelay(40);

        barberManagementService.cancel(
                actors.barber().getUser().getId(), appointment.getId(), "Emergency");

        BarberReputation reputation = barberReputation(actors);
        assertEquals(92, reputation.getScore());
        assertEquals(1, reputation.getCancellationCount());
        assertEquals(0, reputation.getDelayCount());
        assertEquals(List.of(ReputationEventType.BARBER_CANCELLATION),
                eventRepository.findByAppointmentIdOrderByIdAsc(appointment.getId())
                        .stream().map(event -> event.getEventType()).toList());
    }

    @Test
    void customerSelfBookingCarriesConsentIntoNoShowPenalty() {
        TestActors actors = actors("451");
        LocalDateTime start = LOCAL_NOW.minusDays(1).withHour(10).withMinute(0);
        AppointmentResponse booked = customerManagementService.book(
                actors.customer().getUser().getId(),
                new CustomerAppointmentBookingRequest(
                        actors.barber().getId(), actors.offering().getId(),
                        start.toLocalDate(), start.toLocalTime()));

        barberManagementService.markNoShow(
                actors.barber().getUser().getId(), booked.id());

        assertEquals(0, customerReputationRepository.count());
        customerManagementService.respondToNoShow(
                actors.customer().getUser().getId(),
                booked.id(),
                com.example.barbershop.entity.NoShowCustomerResponse.CONFIRM_ABSENCE);

        assertEquals(92, customerReputation(actors).getScore());
        assertEquals(1, customerReputation(actors).getNoShowCount());
        assertEquals(List.of(ReputationEventType.CUSTOMER_NO_SHOW),
                eventRepository.findByAppointmentIdOrderByIdAsc(booked.id()).stream()
                        .map(event -> event.getEventType()).toList());
    }

    @Test
    void barberManualGuestNoShowDoesNotPenalizeCustomer() {
        TestActors actors = actors("452");
        LocalDateTime noShowStart = LOCAL_NOW.minusDays(1)
                .withHour(10).withMinute(0);
        AppointmentResponse noShow = barberManagementService.book(
                actors.barber().getUser().getId(),
                new BarberAppointmentBookingRequest(
                        actors.offering().getId(), "Walk in", null,
                        noShowStart.toLocalDate(), noShowStart.toLocalTime()));
        barberManagementService.updateDelay(
                actors.barber().getUser().getId(), noShow.id(), 20);
        barberManagementService.markNoShow(
                actors.barber().getUser().getId(), noShow.id());

        assertEquals(null, appointmentRepository.findById(noShow.id()).orElseThrow()
                .getCustomer());
        assertEquals(0, customerReputationRepository.count());
        assertEquals(98, barberReputation(actors).getScore());
        assertEquals(1, barberReputation(actors).getDelayCount());
        assertEquals(0, eventRepository.findAll().stream()
                .filter(event -> event.getSubjectType()
                        == ReputationSubjectType.CUSTOMER)
                .count());
    }

    @Test
    void legacyServiceNoShowCannotBypassCustomerReviewRequirement() {
        TestActors actors = actors("501");
        Appointment completed = appointment(
                actors, LOCAL_NOW.minusDays(2), true);
        completed.arrive();
        Appointment noShow = appointment(
                actors, LOCAL_NOW.minusDays(3), true);

        appointmentService.complete(completed.getId());
        appointmentService.markNoShow(noShow.getId());

        CustomerReputation customer = customerReputation(actors);
        BarberReputation barber = barberReputation(actors);
        assertEquals(100, customer.getScore());
        assertEquals(1, customer.getCompletedCount());
        assertEquals(0, customer.getNoShowCount());
        assertEquals(1, barber.getCompletedCount());
    }

    private CustomerReputation customerReputation(TestActors actors) {
        return customerReputationRepository
                .findByCustomerId(actors.customer().getId()).orElseThrow();
    }

    private BarberReputation barberReputation(TestActors actors) {
        return barberReputationRepository
                .findByBarberId(actors.barber().getId()).orElseThrow();
    }

    private Appointment appointment(
            TestActors actors,
            LocalDateTime start,
            boolean registered
    ) {
        Appointment appointment = registered
                ? new Appointment(actors.barber(), actors.offering(),
                        actors.customer(), start.toLocalDate(), start.toLocalTime())
                : new Appointment(actors.barber(), actors.offering(),
                        "Guest", "+989121234567",
                        start.toLocalDate(), start.toLocalTime());
        return appointmentRepository.saveAndFlush(appointment);
    }

    private TestActors actors(String suffix) {
        User barberUser = verifiedUser("+98911" + suffix);
        barberUser.approveBarber();
        userRepository.save(barberUser);
        Barber barber = barberRepository.save(new Barber(
                barberUser, "Barber", LocalTime.of(8, 0), LocalTime.of(20, 0)));
        BarberServiceOffering offering = serviceRepository.save(
                new BarberServiceOffering(barber, "Haircut", 30, 400000L));
        User customerUser = userRepository.save(
                verifiedUser("+98922" + suffix));
        Customer customer = customerRepository.save(
                new Customer(customerUser, "Customer"));
        return new TestActors(barber, offering, customer);
    }

    private User verifiedUser(String phone) {
        User user = new User(phone);
        user.verifyPhone();
        return user;
    }

    private record TestActors(
            Barber barber,
            BarberServiceOffering offering,
            Customer customer
    ) {
    }
}
