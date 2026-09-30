package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentConfirmation;
import com.example.barbershop.entity.AppointmentEventType;
import com.example.barbershop.entity.AppointmentHistory;
import com.example.barbershop.entity.AppointmentHistoryAction;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BarberWeeklySchedule;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.BookingSource;
import com.example.barbershop.entity.CancellationReason;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.repository.AppointmentConfirmationRepository;
import com.example.barbershop.repository.AppointmentEventRepository;
import com.example.barbershop.repository.AppointmentHistoryRepository;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.BarberWeeklyScheduleRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.repository.UserRepository;
import com.example.barbershop.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerAppointmentManagementIntegrationTests {

    private static final String CUSTOMER_BASE = "/api/me/appointments/{id}/";
    private static final String BARBER_BASE = "/api/me/barber/appointments/{id}/";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
    private static final ZoneId ZONE = ZoneId.of("Asia/Tehran");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentHistoryRepository historyRepository;
    @Autowired private AppointmentEventRepository eventRepository;
    @Autowired private AppointmentConfirmationRepository confirmationRepository;
    @Autowired private BlockedTimeRepository blockedTimeRepository;
    @Autowired private BarberWeeklyScheduleRepository scheduleRepository;
    @Autowired private EntityManager entityManager;

    @MockitoBean private Clock clock;

    private int phoneSequence;

    @BeforeEach
    void setUpClock() {
        phoneSequence = 0;
        setNow(LocalDateTime.of(DATE, LocalTime.NOON));
    }

    @Test
    void customerReschedulePreservesIdentityConsumesOpportunityAndRecordsAudit()
            throws Exception {
        Fixture fixture = saveFixture(LocalTime.of(18, 0));
        Long appointmentId = fixture.appointment().getId();
        Long barberId = fixture.barber().getId();
        Long serviceId = fixture.service().getId();
        Long customerId = fixture.customer().getId();

        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", appointmentId)
                        .session(sessionFor(fixture.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(DATE.plusDays(1), LocalTime.of(11, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId))
                .andExpect(jsonPath("$.barberId").value(barberId))
                .andExpect(jsonPath("$.serviceId").value(serviceId))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.date").value(DATE.plusDays(1).toString()))
                .andExpect(jsonPath("$.time").value("11:00:00"));

        flushAndClear();
        Appointment rescheduled = appointmentRepository.findById(appointmentId).orElseThrow();
        assertEquals(appointmentId, rescheduled.getId());
        assertEquals(barberId, rescheduled.getBarber().getId());
        assertEquals(serviceId, rescheduled.getServiceOffering().getId());
        assertEquals(customerId, rescheduled.getCustomer().getId());
        assertFalse(rescheduled.isRescheduleAvailable());
        assertFalse(rescheduled.isBarberDelayRemedyAvailable());

        List<AppointmentHistory> history = historyRepository
                .findByAppointmentIdOrderByIdAsc(appointmentId);
        assertEquals(1, history.size());
        assertEquals(AppointmentHistoryAction.APPOINTMENT_RESCHEDULED,
                history.getFirst().getAction());
        assertEquals("date=2026-10-01,time=18:00", history.getFirst().getOldValue());
        assertEquals("date=2026-10-02,time=11:00,rescheduleType=NORMAL",
                history.getFirst().getNewValue());
        assertEquals(List.of(AppointmentEventType.APPOINTMENT_RESCHEDULED),
                eventRepository.findByAppointmentId(appointmentId).stream()
                        .map(event -> event.getType()).toList());

        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", appointmentId)
                        .session(sessionFor(fixture.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(DATE.plusDays(2), LocalTime.of(11, 0))))
                .andExpect(status().isBadRequest());
        assertEquals(DATE.plusDays(1), appointmentRepository.findById(appointmentId)
                .orElseThrow().getDate());
    }

    @Test
    void exactCutoffAllowsNormalRescheduleButOneSecondInsideRejectsIt()
            throws Exception {
        Fixture boundary = saveFixture(LocalTime.of(18, 0));
        setNow(LocalDateTime.of(DATE, LocalTime.of(16, 0)));

        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", boundary.appointment().getId())
                        .session(sessionFor(boundary.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(DATE.plusDays(1), LocalTime.of(10, 0))))
                .andExpect(status().isOk());

        Fixture inside = saveFixture(LocalTime.of(18, 0));
        setNow(LocalDateTime.of(DATE, LocalTime.of(16, 0, 1)));

        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", inside.appointment().getId())
                        .session(sessionFor(inside.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(DATE.plusDays(1), LocalTime.of(10, 0))))
                .andExpect(status().isBadRequest());
        assertEquals(DATE, appointmentRepository.findById(inside.appointment().getId())
                .orElseThrow().getDate());
    }

    @Test
    void barberDelayRestoresWaivesAndCanRestoreRescheduleOpportunityAgain()
            throws Exception {
        Fixture fixture = saveFixture(LocalTime.of(18, 0));
        Long appointmentId = fixture.appointment().getId();
        MockHttpSession customerSession = sessionFor(fixture.customerUser());
        MockHttpSession barberSession = sessionFor(fixture.barberUser());

        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", appointmentId)
                        .session(customerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(DATE.plusDays(1), LocalTime.of(18, 0))))
                .andExpect(status().isOk());
        assertFalse(appointmentRepository.findById(appointmentId).orElseThrow()
                .isRescheduleAvailable());

        mockMvc.perform(patch(BARBER_BASE + "delay", appointmentId)
                        .session(barberSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delayMinutes\":20}"))
                .andExpect(status().isOk());
        Appointment delayed = appointmentRepository.findById(appointmentId).orElseThrow();
        assertTrue(delayed.isRescheduleAvailable());
        assertTrue(delayed.isBarberDelayRemedyAvailable());

        setNow(LocalDateTime.of(DATE.plusDays(1), LocalTime.of(17, 59)));
        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", appointmentId)
                        .session(customerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(DATE.plusDays(2), LocalTime.of(18, 0))))
                .andExpect(status().isOk());

        Appointment remedyUsed = appointmentRepository.findById(appointmentId).orElseThrow();
        assertFalse(remedyUsed.isRescheduleAvailable());
        assertFalse(remedyUsed.isBarberDelayRemedyAvailable());
        assertNull(remedyUsed.getDelayMinutes());
        assertNull(remedyUsed.getExpectedArrivalTime());

        mockMvc.perform(patch(BARBER_BASE + "delay", appointmentId)
                        .session(barberSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delayMinutes\":10}"))
                .andExpect(status().isOk());
        Appointment delayedAgain = appointmentRepository.findById(appointmentId).orElseThrow();
        assertTrue(delayedAgain.isRescheduleAvailable());
        assertTrue(delayedAgain.isBarberDelayRemedyAvailable());
    }

    @Test
    void removingBarberDelayDoesNotRevokeGrantedRemedy() throws Exception {
        Fixture fixture = saveFixture(LocalTime.of(18, 0));
        Long appointmentId = fixture.appointment().getId();
        MockHttpSession barberSession = sessionFor(fixture.barberUser());

        mockMvc.perform(patch(BARBER_BASE + "delay", appointmentId)
                        .session(barberSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delayMinutes\":15}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete(BARBER_BASE + "delay", appointmentId)
                        .session(barberSession))
                .andExpect(status().isOk());

        Appointment delayRemoved = appointmentRepository.findById(appointmentId).orElseThrow();
        assertNull(delayRemoved.getDelayMinutes());
        assertNull(delayRemoved.getExpectedArrivalTime());
        assertTrue(delayRemoved.isRescheduleAvailable());
        assertTrue(delayRemoved.isBarberDelayRemedyAvailable());

        setNow(LocalDateTime.of(DATE, LocalTime.of(17, 59)));
        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", appointmentId)
                        .session(sessionFor(fixture.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(DATE.plusDays(1), LocalTime.of(18, 0))))
                .andExpect(status().isOk());
    }

    @Test
    void cancellationClassifiesEarlyLateAndBarberDelayCasesDurably()
            throws Exception {
        Fixture early = saveFixture(LocalTime.of(18, 0));
        setNow(LocalDateTime.of(DATE, LocalTime.of(16, 0)));
        cancelAndAssert(early, CancellationReason.CUSTOMER_EARLY);

        Fixture late = saveFixture(LocalTime.of(18, 0));
        setNow(LocalDateTime.of(DATE, LocalTime.of(16, 0, 1)));
        cancelAndAssert(late, CancellationReason.CUSTOMER_LATE);

        Fixture delay = saveFixture(LocalTime.of(18, 0));
        delay.appointment().updateDelay(20);
        appointmentRepository.flush();
        setNow(LocalDateTime.of(DATE, LocalTime.of(17, 59)));
        cancelAndAssert(delay, CancellationReason.BARBER_DELAY);
    }

    @Test
    void customerCannotModifyAnotherCustomersAppointmentOrGuestAppointment()
            throws Exception {
        Fixture owner = saveFixture(LocalTime.of(18, 0));
        User otherUser = saveCustomerUser();
        customerRepository.save(new Customer(otherUser, "Other customer"));
        MockHttpSession otherSession = sessionFor(otherUser);

        assertBothOperationsForbidden(owner.appointment().getId(), otherSession);

        Appointment guest = appointmentRepository.save(new Appointment(
                owner.barber(), owner.service(), "Guest", "+989121111111",
                DATE, LocalTime.of(17, 0)));
        assertBothOperationsForbidden(guest.getId(), sessionFor(owner.customerUser()));
        assertEquals(AppointmentStatus.BOOKED, guest.getStatus());
    }

    @Test
    void customerAppointmentOperationsRequireAuthentication() throws Exception {
        Fixture fixture = saveFixture(LocalTime.of(18, 0));

        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", fixture.appointment().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(DATE.plusDays(1), LocalTime.of(10, 0))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(CUSTOMER_BASE + "cancel", fixture.appointment().getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyCustomerManagementEndpointsAreNotExposed() throws Exception {
        Fixture fixture = saveFixture(LocalTime.of(18, 0));
        Long appointmentId = fixture.appointment().getId();
        MockHttpSession session = sessionFor(fixture.customerUser());
        String legacyReschedule = "{\"serviceId\":" + fixture.service().getId()
                + ",\"date\":\"" + DATE.plusDays(1)
                + "\",\"time\":\"10:00\"}";

        for (MockHttpSession requestSession : List.of(new MockHttpSession(), session)) {
            mockMvc.perform(patch("/api/appointments/{id}/cancel", appointmentId)
                            .session(requestSession))
                    .andExpect(status().isNotFound());
            mockMvc.perform(patch("/api/appointments/{id}/reschedule", appointmentId)
                            .session(requestSession)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(legacyReschedule))
                    .andExpect(status().isNotFound());
        }

        Appointment unchanged = appointmentRepository.findById(appointmentId).orElseThrow();
        assertEquals(AppointmentStatus.BOOKED, unchanged.getStatus());
        assertEquals(DATE, unchanged.getDate());
        assertEquals(LocalTime.of(18, 0), unchanged.getTime());
        assertTrue(unchanged.isRescheduleAvailable());
        assertNull(unchanged.getCancellationReason());
    }

    @Test
    void terminalAppointmentsCannotBeRescheduledOrCancelledByCustomer()
            throws Exception {
        for (AppointmentStatus terminal : List.of(
                AppointmentStatus.ARRIVED,
                AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.NO_SHOW)) {
            Fixture fixture = saveFixture(LocalTime.of(18, 0));
            moveToStatus(fixture.appointment(), terminal);
            appointmentRepository.flush();
            MockHttpSession session = sessionFor(fixture.customerUser());

            mockMvc.perform(patch(CUSTOMER_BASE + "reschedule",
                            fixture.appointment().getId())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rescheduleJson(DATE.plusDays(1), LocalTime.of(10, 0))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(patch(CUSTOMER_BASE + "cancel", fixture.appointment().getId())
                            .session(session))
                    .andExpect(status().isBadRequest());
            assertEquals(terminal, appointmentRepository
                    .findById(fixture.appointment().getId()).orElseThrow().getStatus());
        }
    }

    @Test
    void customerRescheduleUsesExistingConflictBlockedTimeAndScheduleRules()
            throws Exception {
        Fixture fixture = saveFixture(LocalTime.of(18, 0));
        MockHttpSession session = sessionFor(fixture.customerUser());
        LocalDate targetDate = DATE.plusDays(1);
        appointmentRepository.save(new Appointment(
                fixture.barber(), fixture.service(),
                customerRepository.save(new Customer("Conflict", "+989121234567")),
                targetDate, LocalTime.of(11, 0)));

        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", fixture.appointment().getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(targetDate, LocalTime.of(11, 0))))
                .andExpect(status().isConflict());

        blockedTimeRepository.save(new BlockedTime(
                fixture.barber(), targetDate, LocalTime.NOON,
                LocalTime.of(12, 30), "break"));
        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", fixture.appointment().getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(targetDate, LocalTime.NOON)))
                .andExpect(status().isConflict());

        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", fixture.appointment().getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(targetDate, LocalTime.of(20, 0))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", fixture.appointment().getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(targetDate, LocalTime.of(13, 0))))
                .andExpect(status().isOk());
    }

    @Test
    void inactiveWeeklyScheduleRejectsCustomerReschedule() throws Exception {
        Fixture fixture = saveFixture(LocalTime.of(18, 0));
        LocalDate targetDate = DATE.plusDays(1);
        scheduleRepository.save(new BarberWeeklySchedule(
                fixture.barber(), targetDate.getDayOfWeek(), null, null, false));

        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", fixture.appointment().getId())
                        .session(sessionFor(fixture.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(targetDate, LocalTime.of(11, 0))))
                .andExpect(status().isBadRequest());
        assertEquals(DATE, appointmentRepository.findById(fixture.appointment().getId())
                .orElseThrow().getDate());
    }

    @Test
    void rejectedAndExpiredConfirmationsCannotBeResurrected() throws Exception {
        Fixture rejected = saveFixture(LocalTime.of(18, 0), BookingSource.BARBER);
        rejected.appointment().rejectBooking();
        Fixture expired = saveFixture(LocalTime.of(17, 0), BookingSource.BARBER);
        expired.appointment().expireBooking();
        Fixture overdue = saveFixture(LocalTime.of(16, 0), BookingSource.BARBER);
        confirmationRepository.save(new AppointmentConfirmation(
                overdue.appointment(), "654321", LocalDateTime.now().minusMinutes(1)));
        appointmentRepository.flush();

        for (Fixture fixture : List.of(rejected, expired, overdue)) {
            MockHttpSession session = sessionFor(fixture.customerUser());
            mockMvc.perform(patch(CUSTOMER_BASE + "reschedule",
                            fixture.appointment().getId())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rescheduleJson(DATE.plusDays(1), LocalTime.of(10, 0))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(patch(CUSTOMER_BASE + "cancel", fixture.appointment().getId())
                            .session(session))
                    .andExpect(status().isBadRequest());
        }

        assertEquals(BookingConfirmationStatus.REJECTED, appointmentRepository
                .findById(rejected.appointment().getId()).orElseThrow()
                .getConfirmationStatus());
        assertEquals(BookingConfirmationStatus.EXPIRED, appointmentRepository
                .findById(expired.appointment().getId()).orElseThrow()
                .getConfirmationStatus());
        assertEquals(BookingConfirmationStatus.EXPIRED, appointmentRepository
                .findById(overdue.appointment().getId()).orElseThrow()
                .getConfirmationStatus());
        assertNull(confirmationRepository.findByAppointmentId(
                overdue.appointment().getId()).orElseThrow().getCode());
    }

    @Test
    void activePendingConfirmationIdentityIsPreservedDuringReschedule()
            throws Exception {
        Fixture fixture = saveFixture(LocalTime.of(18, 0), BookingSource.BARBER);
        AppointmentConfirmation confirmation = confirmationRepository.save(
                new AppointmentConfirmation(
                        fixture.appointment(), "123456",
                        LocalDateTime.now().plusDays(1)));
        Long confirmationId = confirmation.getId();

        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", fixture.appointment().getId())
                        .session(sessionFor(fixture.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(DATE.plusDays(1), LocalTime.of(11, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationStatus").value("PENDING"));

        AppointmentConfirmation preserved = confirmationRepository
                .findByAppointmentId(fixture.appointment().getId()).orElseThrow();
        assertEquals(confirmationId, preserved.getId());
        assertEquals("123456", preserved.getCode());
        assertEquals(BookingConfirmationStatus.PENDING, appointmentRepository
                .findById(fixture.appointment().getId()).orElseThrow()
                .getConfirmationStatus());
    }

    private void cancelAndAssert(Fixture fixture, CancellationReason expectedReason)
            throws Exception {
        Long appointmentId = fixture.appointment().getId();
        mockMvc.perform(patch(CUSTOMER_BASE + "cancel", appointmentId)
                        .session(sessionFor(fixture.customerUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason")
                        .value(expectedReason.name()));

        Appointment cancelled = appointmentRepository.findById(appointmentId).orElseThrow();
        assertEquals(expectedReason, cancelled.getCancellationReason());
        List<AppointmentHistory> history = historyRepository
                .findByAppointmentIdOrderByIdAsc(appointmentId);
        assertEquals(1, history.size());
        assertEquals(AppointmentHistoryAction.CANCELLED, history.getFirst().getAction());
        assertTrue(history.getFirst().getNewValue().contains("reason=" + expectedReason));
        assertEquals(List.of(AppointmentEventType.APPOINTMENT_CANCELLED),
                eventRepository.findByAppointmentId(appointmentId).stream()
                        .map(event -> event.getType()).toList());
    }

    private void assertBothOperationsForbidden(Long appointmentId, MockHttpSession session)
            throws Exception {
        mockMvc.perform(patch(CUSTOMER_BASE + "reschedule", appointmentId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleJson(DATE.plusDays(1), LocalTime.of(10, 0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(CUSTOMER_BASE + "cancel", appointmentId)
                        .session(session))
                .andExpect(status().isForbidden());
    }

    private void moveToStatus(Appointment appointment, AppointmentStatus status) {
        switch (status) {
            case ARRIVED -> appointment.arrive();
            case COMPLETED -> {
                appointment.arrive();
                appointment.complete();
            }
            case CANCELLED -> appointment.cancel(CancellationReason.CUSTOMER_REQUEST, null);
            case NO_SHOW -> appointment.markNoShow();
            default -> throw new IllegalArgumentException("Terminal status required");
        }
    }

    private Fixture saveFixture(LocalTime time) {
        return saveFixture(time, BookingSource.CUSTOMER);
    }

    private Fixture saveFixture(LocalTime time, BookingSource source) {
        User barberUser = saveBarberUser();
        Barber barber = barberRepository.save(new Barber(
                barberUser, "Barber", LocalTime.of(10, 0), LocalTime.of(20, 0)));
        BarberServiceOffering service = serviceRepository.save(
                new BarberServiceOffering(barber, "Haircut", 30, 400000L));
        User customerUser = saveCustomerUser();
        Customer customer = customerRepository.save(new Customer(customerUser, "Customer"));
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber, service, customer, source, DATE, time));
        return new Fixture(barberUser, customerUser, barber, service, customer, appointment);
    }

    private User saveBarberUser() {
        User user = verifiedUser(nextPhone());
        user.approveBarber();
        return userRepository.save(user);
    }

    private User saveCustomerUser() {
        return userRepository.save(verifiedUser(nextPhone()));
    }

    private User verifiedUser(String phone) {
        User user = new User(phone);
        user.verifyPhone();
        return user;
    }

    private String nextPhone() {
        phoneSequence++;
        return "+9891200" + String.format("%05d", phoneSequence);
    }

    private MockHttpSession sessionFor(User user) {
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(UserRole::name)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(user.getId()), null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context);
        return session;
    }

    private String rescheduleJson(LocalDate date, LocalTime time) {
        return "{\"date\":\"" + date + "\",\"time\":\"" + time + "\"}";
    }

    private void setNow(LocalDateTime now) {
        when(clock.getZone()).thenReturn(ZONE);
        when(clock.instant()).thenReturn(now.atZone(ZONE).toInstant());
    }

    private void flushAndClear() {
        appointmentRepository.flush();
        historyRepository.flush();
        eventRepository.flush();
        entityManager.clear();
    }

    private record Fixture(
            User barberUser,
            User customerUser,
            Barber barber,
            BarberServiceOffering service,
            Customer customer,
            Appointment appointment
    ) {
    }
}
