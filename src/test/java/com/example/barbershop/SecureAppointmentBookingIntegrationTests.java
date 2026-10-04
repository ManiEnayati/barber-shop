package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.BookingSource;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
import com.example.barbershop.repository.AppointmentConfirmationRepository;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import com.example.barbershop.repository.UserRepository;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.AppointmentService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecureAppointmentBookingIntegrationTests {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 5);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentConfirmationRepository confirmationRepository;
    @Autowired private AppointmentService appointmentService;
    @Autowired private BlockedTimeRepository blockedTimeRepository;

    @Test
    void verifiedCustomerSelfBookingUsesSessionIdentityAndImmediatelyReservesSlot()
            throws Exception {
        Actors actors = actors("101");

        mockMvc.perform(post("/api/me/appointments")
                        .session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBookingJson(
                                actors.barber(), actors.offering(), "10:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(actors.customer().getId()))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.bookingSource").value("CUSTOMER"))
                .andExpect(jsonPath("$.customerAccepted").value(true))
                .andExpect(jsonPath("$.confirmationStatus").value("CONFIRMED"));

        Appointment appointment = appointmentRepository.findAll().getFirst();
        assertEquals(BookingSource.CUSTOMER, appointment.getBookingSource());
        assertTrue(appointment.isCustomerAccepted());
        assertEquals(0, confirmationRepository.count());

        mockMvc.perform(post("/api/me/appointments")
                        .session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBookingJson(
                                actors.barber(), actors.offering(), "10:00")))
                .andExpect(status().isConflict());
    }

    @Test
    void customerBookingRequiresAuthenticationAndRejectsIdentityOrSourceFields()
            throws Exception {
        Actors actors = actors("102");
        String valid = customerBookingJson(
                actors.barber(), actors.offering(), "11:00");

        mockMvc.perform(post("/api/me/appointments")
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/me/appointments")
                        .session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace("{", "{\"customerId\":999,")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/me/appointments")
                        .session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid.replace("{", "{\"bookingSource\":\"BARBER\",")))
                .andExpect(status().isBadRequest());

        assertEquals(0, appointmentRepository.count());
    }

    @Test
    void secureCustomerBookingRequiresLinkedVerifiedCustomerIdentityAndManualApiRejectsCustomerId()
            throws Exception {
        Actors actors = actors("109");
        User verifiedWithoutProfile = userRepository.save(
                verifiedUser("+989130010110"));

        mockMvc.perform(post("/api/me/appointments")
                        .session(sessionFor(verifiedWithoutProfile))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBookingJson(
                                actors.barber(), actors.offering(), "11:30")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(actors.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestManualJson(
                                actors.offering(), "Guest", null, "12:30")
                                .replace("{", "{\"customerId\":"
                                        + actors.customer().getId() + ",")))
                .andExpect(status().isBadRequest());

        assertEquals(0, appointmentRepository.count());
    }

    @Test
    void barberManualGuestBookingIsActiveUnacceptedAndNeverExpires()
            throws Exception {
        Actors actors = actors("103");

        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(actors.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestManualJson(
                                actors.offering(), "Walk in", null, "12:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.barberId").value(actors.barber().getId()))
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.guestName").value("Walk in"))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.bookingSource").value("BARBER"))
                .andExpect(jsonPath("$.customerAccepted").value(false))
                .andExpect(jsonPath("$.confirmationStatus").value("NOT_REQUIRED"));

        Appointment appointment = appointmentRepository.findAll().getFirst();
        assertEquals(0, confirmationRepository.count());
        assertEquals(0, appointmentService.expirePendingConfirmations());
        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
        assertEquals(BookingConfirmationStatus.NOT_REQUIRED,
                appointment.getConfirmationStatus());

        mockMvc.perform(post("/api/me/appointments")
                        .session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBookingJson(
                                actors.barber(), actors.offering(), "12:00")))
                .andExpect(status().isConflict());

        assertNull(appointment.getCustomer());
    }

    @Test
    void barberManualGuestBookingNeverLinksByPhoneAndRejectsContradictoryIdentity()
            throws Exception {
        Actors actors = actors("104");
        String guestJson = "{\"serviceId\":" + actors.offering().getId()
                + ",\"guestName\":\" Ali \""
                + ",\"guestPhone\":\"09130010104\""
                + ",\"date\":\"" + DATE + "\",\"time\":\"13:00\"}";

        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(actors.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(guestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.guestName").value("Ali"))
                .andExpect(jsonPath("$.bookingSource").value("BARBER"))
                .andExpect(jsonPath("$.customerAccepted").value(false))
                .andExpect(jsonPath("$.confirmationStatus").value("NOT_REQUIRED"));

        Appointment guest = appointmentRepository.findAll().getFirst();
        assertNull(guest.getCustomer());
        assertEquals(actors.customer().getPhone(), guest.getGuestPhone());
        assertEquals(0, confirmationRepository.count());

        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(actors.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestManualJson(
                                actors.offering(), "Guest", null, "14:00")
                                .replace("{", "{\"customerId\":"
                                        + actors.customer().getId() + ",")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(actors.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":" + actors.offering().getId()
                                + ",\"guestName\":\"No phone\""
                                + ",\"date\":\"" + DATE
                                + "\",\"time\":\"14:30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guestName").value("No phone"))
                .andExpect(jsonPath("$.guestPhone").doesNotExist());

        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(actors.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":" + actors.offering().getId()
                                + ",\"date\":\"" + DATE
                                + "\",\"time\":\"16:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void barberCannotForgeOwnerOrUseAnotherBarbersService() throws Exception {
        Actors owner = actors("105");
        Actors other = actors("106");
        String otherService = guestManualJson(
                other.offering(), "Guest", null, "15:00");

        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(owner.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(otherService))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(owner.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestManualJson(
                                owner.offering(), "Guest", null, "16:30")
                                .replace("{", "{\"customerAccepted\":true,")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(owner.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestManualJson(
                                owner.offering(), "Guest", null, "17:00")
                                .replace("{", "{\"status\":\"BOOKED\",")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(owner.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestManualJson(
                                owner.offering(), "Guest", null, "15:00")
                                .replace("{", "{\"barberId\":"
                                        + other.barber().getId() + ",")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(owner.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestManualJson(
                                owner.offering(), "Guest", null, "16:00")
                                .replace("{", "{\"bookingSource\":\"CUSTOMER\",")))
                .andExpect(status().isBadRequest());

        assertEquals(0, appointmentRepository.count());
    }

    @Test
    void bothSecureBookingModesEnforceBlockedTimeAndAppointmentOverlap()
            throws Exception {
        Actors actors = actors("108");
        blockedTimeRepository.save(new BlockedTime(
                actors.barber(), DATE, LocalTime.of(14, 0),
                LocalTime.of(14, 30), "Break"));

        mockMvc.perform(post("/api/me/appointments")
                        .session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBookingJson(
                                actors.barber(), actors.offering(), "14:00")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(actors.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestManualJson(
                                actors.offering(), "Guest", null, "14:00")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/me/appointments")
                        .session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBookingJson(
                                actors.barber(), actors.offering(), "15:00")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/me/barber/appointments")
                        .session(sessionFor(actors.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(guestManualJson(
                                actors.offering(), "Guest", null, "15:00")))
                .andExpect(status().isConflict());

        assertEquals(1, appointmentRepository.count());
    }

    @Test
    void removedPublicBookingRouteReturnsNotFoundForAnonymousAndAuthenticated()
            throws Exception {
        Actors actors = actors("107");
        String legacy = customerBookingJson(
                actors.barber(), actors.offering(), "16:00");

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON).content(legacy))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/appointments")
                        .session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(legacy))
                .andExpect(status().isNotFound());
        assertEquals(0, appointmentRepository.count());
    }

    private Actors actors(String suffix) {
        User barberUser = verifiedUser("+989120010" + suffix);
        barberUser.approveBarber();
        userRepository.save(barberUser);
        Barber barber = barberRepository.save(new Barber(
                barberUser, "Barber", LocalTime.of(8, 0), LocalTime.of(20, 0)));
        BarberServiceOffering offering = serviceRepository.save(
                new BarberServiceOffering(barber, "Haircut", 30, 400000L));
        User customerUser = userRepository.save(
                verifiedUser("+989130010" + suffix));
        Customer customer = customerRepository.save(
                new Customer(customerUser, "Customer"));
        return new Actors(barber, offering, customerUser, customer);
    }

    private String customerBookingJson(
            Barber barber,
            BarberServiceOffering offering,
            String time
    ) {
        return "{\"barberId\":" + barber.getId()
                + ",\"serviceId\":" + offering.getId()
                + ",\"date\":\"" + DATE + "\",\"time\":\"" + time + "\"}";
    }

    private String guestManualJson(
            BarberServiceOffering offering,
            String guestName,
            String guestPhone,
            String time
    ) {
        return "{\"serviceId\":" + offering.getId()
                + ",\"guestName\":\"" + guestName + "\""
                + (guestPhone == null ? "" : ",\"guestPhone\":\""
                        + guestPhone + "\"")
                + ",\"date\":\"" + DATE + "\",\"time\":\"" + time + "\"}";
    }

    private User verifiedUser(String phone) {
        User user = new User(phone);
        user.verifyPhone();
        return user;
    }

    private MockHttpSession sessionFor(User user) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
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
            Barber barber,
            BarberServiceOffering offering,
            User customerUser,
            Customer customer
    ) {
    }
}
