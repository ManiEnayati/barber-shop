package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentNoShowReviewStatus;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BookingSource;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.ReputationEventType;
import com.example.barbershop.entity.ReputationSubjectType;
import com.example.barbershop.entity.User;
import com.example.barbershop.repository.AppointmentNoShowReportRepository;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberReputationRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.repository.CustomerReputationRepository;
import com.example.barbershop.repository.ReputationEventRepository;
import com.example.barbershop.repository.UserRepository;
import com.example.barbershop.security.AuthenticatedUser;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoShowReviewIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentNoShowReportRepository reportRepository;
    @Autowired private CustomerReputationRepository customerReputationRepository;
    @Autowired private BarberReputationRepository barberReputationRepository;
    @Autowired private ReputationEventRepository eventRepository;

    @Test
    void barberReportWaitsForOwningCustomerConfirmationAndPenalizesOnce()
            throws Exception {
        Actors actors = actors("101");
        Appointment appointment = acceptedAppointment(actors, LocalTime.of(10, 0));
        appointment.updateDelay(20);

        mockMvc.perform(patch(
                        "/api/me/barber/appointments/{id}/no-show",
                        appointment.getId()
                ).session(sessionFor(actors.barberUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));

        assertEquals(AppointmentStatus.NO_SHOW, appointment.getStatus());
        assertEquals(AppointmentNoShowReviewStatus.PENDING_CUSTOMER,
                reportRepository.findByAppointmentId(appointment.getId())
                        .orElseThrow().getStatus());
        assertTrue(customerReputationRepository.findByCustomerId(
                actors.customer().getId()).isEmpty());
        assertFalse(hasCustomerNoShowEvent(appointment));
        assertEquals(98, barberReputationRepository
                .findByBarberId(actors.barber().getId()).orElseThrow().getScore());

        mockMvc.perform(post(
                        "/api/me/appointments/{id}/no-show-response",
                        appointment.getId()
                ).session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"CONFIRM_ABSENCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("CUSTOMER_CONFIRMED_ABSENCE"));

        assertEquals(92, customerReputationRepository.findByCustomerId(
                actors.customer().getId()).orElseThrow().getScore());
        assertEquals(1, customerReputationRepository.findByCustomerId(
                actors.customer().getId()).orElseThrow().getNoShowCount());
        assertTrue(hasCustomerNoShowEvent(appointment));

        mockMvc.perform(post(
                        "/api/me/appointments/{id}/no-show-response",
                        appointment.getId()
                ).session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"CONFIRM_ABSENCE\"}"))
                .andExpect(status().isConflict());
        assertEquals(1, customerReputationRepository.findByCustomerId(
                actors.customer().getId()).orElseThrow().getNoShowCount());
    }

    @Test
    void disputeIsFinalAndOnlyTheOwningAuthenticatedCustomerCanRespond()
            throws Exception {
        Actors actors = actors("201");
        Actors other = actors("202");
        Appointment appointment = acceptedAppointment(actors, LocalTime.of(11, 0));
        mockMvc.perform(patch(
                        "/api/me/barber/appointments/{id}/no-show",
                        appointment.getId()
                ).session(sessionFor(actors.barberUser())))
                .andExpect(status().isOk());

        String dispute = "{\"response\":\"DISPUTE\"}";
        mockMvc.perform(post(
                        "/api/me/appointments/{id}/no-show-response",
                        appointment.getId()
                ).contentType(MediaType.APPLICATION_JSON).content(dispute))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(
                        "/api/me/appointments/{id}/no-show-response",
                        appointment.getId()
                ).session(sessionFor(other.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(dispute))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(
                        "/api/me/appointments/{id}/no-show-response",
                        appointment.getId()
                ).session(sessionFor(actors.barberUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(dispute))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/api/me/appointments/{id}/no-show-response",
                        appointment.getId()
                ).session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(dispute))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"));
        mockMvc.perform(post(
                        "/api/me/appointments/{id}/no-show-response",
                        appointment.getId()
                ).session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"CONFIRM_ABSENCE\"}"))
                .andExpect(status().isConflict());

        assertTrue(customerReputationRepository.findByCustomerId(
                actors.customer().getId()).isEmpty());
        assertFalse(hasCustomerNoShowEvent(appointment));
    }

    @Test
    void claimedBarberGuestRemainsUnacceptedAndCannotReceiveNoShowPenalty()
            throws Exception {
        Actors actors = actors("301");
        Appointment guest = appointmentRepository.saveAndFlush(
                Appointment.barberManualGuestBooking(
                        actors.barber(), actors.offering(), "Matching guest",
                        actors.customerUser().getPhone(),
                        LocalDate.now().minusDays(1), LocalTime.of(12, 0)));

        assertNull(guest.getCustomer());
        mockMvc.perform(post(
                        "/api/me/appointment-claims/{id}/confirm", guest.getId()
                ).session(sessionFor(actors.customerUser())))
                .andExpect(status().isNoContent());

        assertEquals(BookingSource.BARBER, guest.getBookingSource());
        assertFalse(guest.isCustomerAccepted());
        assertEquals(actors.customer().getId(), guest.getCustomer().getId());

        mockMvc.perform(patch(
                        "/api/me/barber/appointments/{id}/no-show", guest.getId()
                ).session(sessionFor(actors.barberUser())))
                .andExpect(status().isOk());

        assertEquals(AppointmentNoShowReviewStatus.NOT_APPLICABLE,
                reportRepository.findByAppointmentId(guest.getId())
                        .orElseThrow().getStatus());
        mockMvc.perform(post(
                        "/api/me/appointments/{id}/no-show-response", guest.getId()
                ).session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"CONFIRM_ABSENCE\"}"))
                .andExpect(status().isConflict());
        assertTrue(customerReputationRepository.findByCustomerId(
                actors.customer().getId()).isEmpty());
        assertFalse(hasCustomerNoShowEvent(guest));
    }

    @Test
    void unclaimedGuestNoShowIsOperationalOnlyAndCreatesNoIdentity()
            throws Exception {
        Actors actors = actors("401");
        long customerCount = customerRepository.count();
        long userCount = userRepository.count();
        Appointment guest = appointmentRepository.saveAndFlush(
                Appointment.barberManualGuestBooking(
                        actors.barber(), actors.offering(), "Walk in",
                        "+989199998888", LocalDate.now().minusDays(1),
                        LocalTime.of(13, 0)));

        mockMvc.perform(patch(
                        "/api/me/barber/appointments/{id}/no-show", guest.getId()
                ).session(sessionFor(actors.barberUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));

        assertEquals(AppointmentNoShowReviewStatus.NOT_APPLICABLE,
                reportRepository.findByAppointmentId(guest.getId())
                        .orElseThrow().getStatus());
        assertEquals(customerCount, customerRepository.count());
        assertEquals(userCount, userRepository.count());
        assertEquals(0, customerReputationRepository.count());
        assertFalse(hasCustomerNoShowEvent(guest));
    }

    private boolean hasCustomerNoShowEvent(Appointment appointment) {
        return eventRepository.existsByAppointmentIdAndSubjectTypeAndEventType(
                appointment.getId(),
                ReputationSubjectType.CUSTOMER,
                ReputationEventType.CUSTOMER_NO_SHOW);
    }

    private Appointment acceptedAppointment(Actors actors, LocalTime time) {
        return appointmentRepository.saveAndFlush(new Appointment(
                actors.barber(), actors.offering(), actors.customer(),
                LocalDate.now().minusDays(1), time));
    }

    private Actors actors(String suffix) {
        User barberUser = verifiedUser("+989140010" + suffix);
        barberUser.approveBarber();
        userRepository.save(barberUser);
        Barber barber = barberRepository.save(new Barber(
                barberUser, "Barber", LocalTime.of(8, 0), LocalTime.of(20, 0)));
        BarberServiceOffering offering = serviceRepository.save(
                new BarberServiceOffering(barber, "Haircut", 30, 400000L));
        User customerUser = userRepository.save(
                verifiedUser("+989150010" + suffix));
        Customer customer = customerRepository.save(
                new Customer(customerUser, "Customer"));
        return new Actors(barberUser, barber, offering, customerUser, customer);
    }

    private User verifiedUser(String phone) {
        User user = new User(phone);
        user.verifyPhone();
        return user;
    }

    private MockHttpSession sessionFor(User user) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
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

    private record Actors(
            User barberUser,
            Barber barber,
            BarberServiceOffering offering,
            User customerUser,
            Customer customer
    ) {
    }
}
