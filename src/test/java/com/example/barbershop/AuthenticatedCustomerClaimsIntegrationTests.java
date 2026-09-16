package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentClaim;
import com.example.barbershop.entity.AppointmentClaimStatus;
import com.example.barbershop.entity.AppointmentHistory;
import com.example.barbershop.entity.AppointmentHistoryAction;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
import com.example.barbershop.repository.AppointmentClaimRepository;
import com.example.barbershop.repository.AppointmentHistoryRepository;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.repository.UserRepository;
import com.example.barbershop.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticatedCustomerClaimsIntegrationTests {

    private static final String PHONE = "+989121234567";
    private static final LocalDate DATE = LocalDate.of(2026, 9, 25);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentClaimRepository appointmentClaimRepository;
    @Autowired private AppointmentHistoryRepository appointmentHistoryRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void meAndAppointmentClaimsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/appointment-claims"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentVerifiedUserWithoutIdentityParameters() throws Exception {
        User user = saveVerifiedUser(PHONE);

        mockMvc.perform(get("/api/me").session(sessionFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.phone").value(PHONE))
                .andExpect(jsonPath("$.phoneVerified").value(true))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
    }

    @Test
    void customerProfileUsesAuthenticatedPhoneAndDoesNotAttachAppointments()
            throws Exception {
        User user = saveVerifiedUser(PHONE);
        MockHttpSession session = sessionFor(user);
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Appointment guest = appointmentRepository.save(new Appointment(
                barber,
                service,
                "Old guest",
                PHONE,
                DATE,
                LocalTime.of(10, 0)
        ));

        mockMvc.perform(put("/api/me/customer-profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Reza Karimi",
                                  "phone": "+989999999999",
                                  "userId": 999
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reza Karimi"))
                .andExpect(jsonPath("$.phone").value(PHONE));
        mockMvc.perform(put("/api/me/customer-profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reza Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reza Updated"));

        customerRepository.flush();
        appointmentRepository.flush();
        entityManager.clear();
        Customer profile = customerRepository.findByUserId(user.getId()).orElseThrow();
        Appointment unchangedGuest = appointmentRepository
                .findById(guest.getId())
                .orElseThrow();
        assertEquals(1, customerRepository.count());
        assertEquals(user.getId(), profile.getUser().getId());
        assertEquals(PHONE, profile.getPhone());
        assertEquals("Reza Updated", profile.getName());
        assertNull(unchangedGuest.getCustomer());
        assertEquals(0, appointmentClaimRepository.count());
    }

    @Test
    void claimCandidatesIncludeMatchingHistoricalGuestsOnly() throws Exception {
        User user = saveVerifiedUser(PHONE);
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Appointment matching = appointmentRepository.save(new Appointment(
                barber, service, "Matching", "09121234567",
                DATE, LocalTime.of(10, 0)
        ));
        Appointment completed = new Appointment(
                barber, service, "Completed", "00989121234567",
                DATE.minusDays(1), LocalTime.of(11, 0)
        );
        completed.arrive();
        completed.complete();
        appointmentRepository.save(completed);
        appointmentRepository.save(new Appointment(
                barber, service, "No phone", null,
                DATE, LocalTime.of(12, 0)
        ));
        appointmentRepository.save(new Appointment(
                barber, service, "Other phone", "+989111111111",
                DATE, LocalTime.of(13, 0)
        ));

        mockMvc.perform(get("/api/me/appointment-claims")
                        .session(sessionFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(
                        matching.getId().intValue(),
                        completed.getId().intValue()
                )));

        assertNull(appointmentRepository.findById(matching.getId())
                .orElseThrow().getCustomer());
        assertEquals(0, appointmentClaimRepository.count());
    }

    @Test
    void confirmingClaimLinksOriginalAppointmentAndPreservesLifecycle()
            throws Exception {
        User user = saveVerifiedUser(PHONE);
        Customer profile = customerRepository.save(new Customer(user, "Reza"));
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Appointment appointment = new Appointment(
                barber,
                service,
                "Original guest",
                PHONE,
                DATE,
                LocalTime.of(10, 30)
        );
        appointment.arrive();
        appointment.complete();
        appointment = appointmentRepository.save(appointment);
        Long appointmentId = appointment.getId();
        AppointmentStatus originalStatus = appointment.getStatus();
        BookingConfirmationStatus originalConfirmation = appointment
                .getConfirmationStatus();

        mockMvc.perform(post(
                        "/api/me/appointment-claims/{id}/confirm",
                        appointmentId
                ).session(sessionFor(user)))
                .andExpect(status().isNoContent());

        flushAndClear();
        Appointment claimed = appointmentRepository.findById(appointmentId).orElseThrow();
        AppointmentClaim claim = appointmentClaimRepository.findAll().getFirst();
        AppointmentHistory history = appointmentHistoryRepository
                .findByAppointmentIdOrderByIdAsc(appointmentId)
                .getFirst();
        assertEquals(appointmentId, claimed.getId());
        assertEquals(profile.getId(), claimed.getCustomer().getId());
        assertNull(claimed.getGuestName());
        assertNull(claimed.getGuestPhone());
        assertEquals(originalStatus, claimed.getStatus());
        assertEquals(originalConfirmation, claimed.getConfirmationStatus());
        assertEquals(DATE, claimed.getDate());
        assertEquals(LocalTime.of(10, 30), claimed.getTime());
        assertEquals(service.getId(), claimed.getServiceOffering().getId());
        assertEquals(barber.getId(), claimed.getBarber().getId());
        assertEquals(AppointmentClaimStatus.CONFIRMED, claim.getStatus());
        assertEquals(AppointmentHistoryAction.CUSTOMER_CLAIMED, history.getAction());
        assertTrue(history.getOldValue().contains("guestName=Original guest"));
        assertTrue(history.getOldValue().contains("guestPhone=" + PHONE));
        assertEquals("customerId=" + profile.getId(), history.getNewValue());
        assertEquals(1, appointmentRepository.count());

        mockMvc.perform(get("/api/customers/{id}/appointments", profile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(appointmentId));
    }

    @Test
    void confirmingClaimRequiresCustomerProfile() throws Exception {
        User user = saveVerifiedUser(PHONE);
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber, service, "Guest", PHONE,
                DATE, LocalTime.of(10, 0)
        ));

        mockMvc.perform(post(
                        "/api/me/appointment-claims/{id}/confirm",
                        appointment.getId()
                ).session(sessionFor(user)))
                .andExpect(status().isBadRequest());

        assertNull(appointmentRepository.findById(appointment.getId())
                .orElseThrow().getCustomer());
        assertEquals(0, appointmentClaimRepository.count());
    }

    @Test
    void rejectingClaimLeavesGuestAndRemovesCandidate() throws Exception {
        User user = saveVerifiedUser(PHONE);
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber, service, "Not mine", PHONE,
                DATE, LocalTime.of(10, 0)
        ));

        mockMvc.perform(post(
                        "/api/me/appointment-claims/{id}/reject",
                        appointment.getId()
                ).session(sessionFor(user)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/appointment-claims")
                        .session(sessionFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        Appointment unchanged = appointmentRepository
                .findById(appointment.getId())
                .orElseThrow();
        AppointmentClaim claim = appointmentClaimRepository.findAll().getFirst();
        assertNull(unchanged.getCustomer());
        assertEquals("Not mine", unchanged.getGuestName());
        assertEquals(PHONE, unchanged.getGuestPhone());
        assertEquals(AppointmentClaimStatus.REJECTED, claim.getStatus());
        assertEquals(0, appointmentHistoryRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId()).size());
    }

    @Test
    void previousDecisionPreventsAnySecondDecision() throws Exception {
        User user = saveVerifiedUser(PHONE);
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber, service, "Guest", PHONE,
                DATE, LocalTime.of(10, 0)
        ));
        MockHttpSession session = sessionFor(user);
        mockMvc.perform(post(
                        "/api/me/appointment-claims/{id}/reject",
                        appointment.getId()
                ).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(
                        "/api/me/appointment-claims/{id}/reject",
                        appointment.getId()
                ).session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(
                        "/api/me/appointment-claims/{id}/confirm",
                        appointment.getId()
                ).session(session))
                .andExpect(status().isBadRequest());

        assertEquals(1, appointmentClaimRepository.count());
    }

    @Test
    void userCannotClaimAppointmentRegisteredToAnotherPhone() throws Exception {
        User user = saveVerifiedUser(PHONE);
        customerRepository.save(new Customer(user, "Reza"));
        Barber barber = saveBarber();
        BarberServiceOffering service = saveService(barber);
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber, service, "Other", "+989111111111",
                DATE, LocalTime.of(10, 0)
        ));
        MockHttpSession session = sessionFor(user);

        mockMvc.perform(post(
                        "/api/me/appointment-claims/{id}/confirm",
                        appointment.getId()
                ).session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(
                        "/api/me/appointment-claims/{id}/reject",
                        appointment.getId()
                ).session(session))
                .andExpect(status().isBadRequest());

        assertNull(appointmentRepository.findById(appointment.getId())
                .orElseThrow().getCustomer());
        assertEquals(0, appointmentClaimRepository.count());
    }

    private User saveVerifiedUser(String phone) {
        User user = new User(phone);
        user.verifyPhone();
        return userRepository.save(user);
    }

    private MockHttpSession sessionFor(User user) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(user.getId()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
        return session;
    }

    private Barber saveBarber() {
        return barberRepository.save(new Barber(
                "Ali",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
    }

    private BarberServiceOffering saveService(Barber barber) {
        return serviceRepository.save(new BarberServiceOffering(
                barber,
                "Haircut",
                30,
                400000L
        ));
    }

    private void flushAndClear() {
        appointmentClaimRepository.flush();
        appointmentHistoryRepository.flush();
        appointmentRepository.flush();
        customerRepository.flush();
        entityManager.clear();
    }
}
