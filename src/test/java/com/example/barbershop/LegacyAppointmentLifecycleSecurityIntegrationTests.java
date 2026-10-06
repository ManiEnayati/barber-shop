package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentConfirmation;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BookingConfirmationStatus;
import com.example.barbershop.entity.BookingSource;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
import com.example.barbershop.repository.AppointmentConfirmationRepository;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.CustomerRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LegacyAppointmentLifecycleSecurityIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentConfirmationRepository confirmationRepository;

    @Test
    void removedLifecycleUrlsAreDeniedToAnonymousAndAuthenticatedCallers()
            throws Exception {
        TestActors actors = actors("1");
        Appointment appointment = appointmentRepository.save(new Appointment(
                actors.barber(), actors.offering(), actors.customer(),
                LocalDate.of(2026, 10, 8), LocalTime.of(10, 0)));

        for (String action : new String[]{"arrive", "complete", "no-show"}) {
            String url = "/api/appointments/" + appointment.getId() + "/" + action;
            mockMvc.perform(patch(url)).andExpect(status().isUnauthorized());
            mockMvc.perform(patch(url).session(sessionFor(actors.barber().getUser())))
                    .andExpect(status().isForbidden());
        }

        assertEquals(AppointmentStatus.BOOKED, appointmentRepository
                .findById(appointment.getId()).orElseThrow().getStatus());
    }

    @Test
    void publicIdOnlyRejectIsGoneAndOwningCustomerCanStillRejectPendingBooking()
            throws Exception {
        TestActors actors = actors("2");
        Appointment appointment = appointmentRepository.save(new Appointment(
                actors.barber(), actors.offering(), actors.customer(),
                BookingSource.BARBER,
                LocalDate.of(2026, 10, 8), LocalTime.of(11, 0)));
        AppointmentConfirmation confirmation = confirmationRepository.save(
                new AppointmentConfirmation(
                        appointment, "123456", LocalDateTime.now().plusHours(1)));
        String oldUrl = "/api/appointments/" + appointment.getId() + "/reject";

        mockMvc.perform(post(oldUrl)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(oldUrl).session(sessionFor(actors.customerUser())))
                .andExpect(status().isForbidden());
        assertEquals(BookingConfirmationStatus.PENDING,
                appointment.getConfirmationStatus());
        assertEquals("123456", confirmation.getCode());

        TestActors other = actors("3");
        mockMvc.perform(post("/api/me/appointments/{id}/reject",
                        appointment.getId())
                        .session(sessionFor(other.customerUser())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/me/appointments/{id}/reject",
                        appointment.getId())
                        .session(sessionFor(actors.customerUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.confirmationStatus").value("REJECTED"));

        assertEquals(BookingConfirmationStatus.REJECTED,
                appointment.getConfirmationStatus());
        assertNull(confirmation.getCode());
        mockMvc.perform(post("/api/appointments/{id}/confirm", appointment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    private TestActors actors(String suffix) {
        User barberUser = verifiedUser("+98912000100" + suffix);
        barberUser.approveBarber();
        userRepository.save(barberUser);
        Barber barber = barberRepository.save(new Barber(
                barberUser, "Barber", LocalTime.of(8, 0), LocalTime.of(20, 0)));
        BarberServiceOffering offering = serviceRepository.save(
                new BarberServiceOffering(barber, "Haircut", 30, 400000L));

        User customerUser = userRepository.save(
                verifiedUser("+98913000100" + suffix));
        Customer customer = customerRepository.save(
                new Customer(customerUser, "Customer"));
        return new TestActors(barber, offering, customerUser, customer);
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

    private record TestActors(
            Barber barber,
            BarberServiceOffering offering,
            User customerUser,
            Customer customer
    ) {
    }
}
