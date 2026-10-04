package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BookingSource;
import com.example.barbershop.entity.CancellationReason;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerAppointmentListIntegrationTests {

    private static final String PATH = "/api/me/appointments";
    private static final LocalDate DATE = LocalDate.of(2026, 11, 5);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Test
    void listsOnlySessionCustomersActiveAndHistoricalAppointmentsInStableOrder()
            throws Exception {
        Actors owner = actors("201");
        Actors other = actors("202");

        mockMvc.perform(post(PATH)
                        .session(sessionFor(owner.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerBookingJson(owner, DATE, "10:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingSource").value("CUSTOMER"))
                .andExpect(jsonPath("$.customerAccepted").value(true));
        Appointment online = appointmentRepository
                .findByCustomerId(owner.customer().getId()).getFirst();

        Appointment cancelled = appointmentRepository.save(new Appointment(
                owner.barber(), owner.offering(), owner.customer(),
                DATE.plusDays(1), LocalTime.of(9, 0)));
        cancelled.cancelByCustomer(CancellationReason.CUSTOMER_EARLY);

        Appointment completed = appointmentRepository.save(new Appointment(
                owner.barber(), owner.offering(), owner.customer(),
                DATE.plusDays(2), LocalTime.of(10, 0)));
        completed.arrive();
        completed.complete();

        Appointment firstTie = appointmentRepository.saveAndFlush(new Appointment(
                owner.barber(), owner.offering(), owner.customer(),
                DATE.plusDays(3), LocalTime.of(11, 0)));
        Appointment secondTie = appointmentRepository.saveAndFlush(new Appointment(
                owner.barber(), owner.offering(), owner.customer(),
                DATE.plusDays(3), LocalTime.of(11, 0)));

        Appointment anotherCustomersAppointment = appointmentRepository.save(
                new Appointment(other.barber(), other.offering(), other.customer(),
                        DATE.plusDays(4), LocalTime.NOON));

        mockMvc.perform(get(PATH)
                        .session(sessionFor(owner.customerUser()))
                        .param("customerId", other.customer().getId().toString())
                        .param("userId", other.customerUser().getId().toString())
                        .param("phone", other.customer().getPhone())
                        .param("bookingSource", BookingSource.BARBER.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").value(secondTie.getId()))
                .andExpect(jsonPath("$[1].id").value(firstTie.getId()))
                .andExpect(jsonPath("$[2].id").value(completed.getId()))
                .andExpect(jsonPath("$[2].status").value("COMPLETED"))
                .andExpect(jsonPath("$[3].id").value(cancelled.getId()))
                .andExpect(jsonPath("$[3].status").value("CANCELLED"))
                .andExpect(jsonPath("$[4].id").value(online.getId()))
                .andExpect(jsonPath("$[4].bookingSource").value("CUSTOMER"))
                .andExpect(jsonPath("$[4].customerAccepted").value(true))
                .andExpect(jsonPath("$[4].confirmationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].customerId").value(owner.customer().getId()))
                .andExpect(jsonPath("$[1].customerId").value(owner.customer().getId()))
                .andExpect(jsonPath("$[2].customerId").value(owner.customer().getId()))
                .andExpect(jsonPath("$[3].customerId").value(owner.customer().getId()))
                .andExpect(jsonPath("$[4].customerId").value(owner.customer().getId()));

        assertFalse(appointmentRepository
                .findByCustomerId(owner.customer().getId()).stream()
                .anyMatch(appointment -> appointment.getId()
                        .equals(anotherCustomersAppointment.getId())));
    }

    @Test
    void claimedManualGuestAppearsButMatchingUnclaimedGuestDoesNot()
            throws Exception {
        Actors actors = actors("203");
        Appointment claimable = appointmentRepository.save(
                Appointment.barberManualGuestBooking(
                        actors.barber(), actors.offering(), "Claimed guest",
                        actors.customer().getPhone(), DATE, LocalTime.of(12, 0)));
        Appointment unclaimed = appointmentRepository.save(
                Appointment.barberManualGuestBooking(
                        actors.barber(), actors.offering(), "Unclaimed guest",
                        actors.customer().getPhone(), DATE, LocalTime.of(13, 0)));

        mockMvc.perform(get(PATH).session(sessionFor(actors.customerUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertNull(claimable.getCustomer());
        assertNull(unclaimed.getCustomer());

        mockMvc.perform(post("/api/me/appointment-claims/{id}/confirm",
                        claimable.getId())
                        .session(sessionFor(actors.customerUser())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PATH).session(sessionFor(actors.customerUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(claimable.getId()))
                .andExpect(jsonPath("$[0].customerId")
                        .value(actors.customer().getId()))
                .andExpect(jsonPath("$[0].bookingSource").value("BARBER"))
                .andExpect(jsonPath("$[0].customerAccepted").value(false));

        assertNull(appointmentRepository.findById(unclaimed.getId())
                .orElseThrow().getCustomer());
    }

    @Test
    void requiresAuthenticatedVerifiedLinkedCustomerAndSupportsEmptyList()
            throws Exception {
        Actors eligible = actors("204");

        mockMvc.perform(get(PATH).session(sessionFor(eligible.customerUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());

        User verifiedWithoutProfile = userRepository.save(
                verifiedUser("+989130019991"));
        mockMvc.perform(get(PATH).session(sessionFor(verifiedWithoutProfile)))
                .andExpect(status().isForbidden());

        User unverifiedUser = userRepository.save(new User("+989130019992"));
        Customer unverifiedCustomer = new Customer(
                "Unverified customer", unverifiedUser.getPhone());
        ReflectionTestUtils.setField(
                unverifiedCustomer, "user", unverifiedUser);
        customerRepository.save(unverifiedCustomer);
        mockMvc.perform(get(PATH).session(sessionFor(unverifiedUser)))
                .andExpect(status().isForbidden());
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
            Actors actors,
            LocalDate date,
            String time
    ) {
        return "{\"barberId\":" + actors.barber().getId()
                + ",\"serviceId\":" + actors.offering().getId()
                + ",\"date\":\"" + date + "\",\"time\":\"" + time + "\"}";
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
            Barber barber,
            BarberServiceOffering offering,
            User customerUser,
            Customer customer
    ) {
    }
}
