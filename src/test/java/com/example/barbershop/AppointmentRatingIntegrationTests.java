package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentRating;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BookingSource;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.RatingRaterType;
import com.example.barbershop.entity.User;
import com.example.barbershop.repository.AppointmentRatingRepository;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberReputationRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.CustomerReputationRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.repository.UserRepository;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.ReputationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentRatingIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentRatingRepository ratingRepository;
    @Autowired private CustomerReputationRepository customerReputationRepository;
    @Autowired private BarberReputationRepository barberReputationRepository;
    @Autowired private ReputationService reputationService;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void customerCanStoreEveryValidRatingValue(int value) throws Exception {
        TestActors actors = actors("10" + value);
        Appointment appointment = completedAppointment(actors, LocalTime.of(10, value));

        mockMvc.perform(post("/api/me/appointments/{id}/rating", appointment.getId())
                        .session(sessionFor(actors.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":" + value + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(appointment.getId()))
                .andExpect(jsonPath("$.raterType").value("CUSTOMER"))
                .andExpect(jsonPath("$.rating").value(value));

        AppointmentRating stored = ratingRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId()).getFirst();
        assertEquals(RatingRaterType.CUSTOMER, stored.getRaterType());
        assertEquals(value, stored.getRating());
    }

    @Test
    void duplicateCustomerRatingReturnsConflict() throws Exception {
        TestActors actors = actors("201");
        Appointment appointment = completedAppointment(actors, LocalTime.of(11, 0));

        rateAsCustomer(actors, appointment, 4).andExpect(status().isCreated());
        rateAsCustomer(actors, appointment, 5).andExpect(status().isConflict());

        assertEquals(1, ratingRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId()).size());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 6})
    void invalidCustomerRatingReturnsBadRequest(int value) throws Exception {
        TestActors actors = actors("30" + Math.abs(value));
        Appointment appointment = completedAppointment(actors, LocalTime.of(12, 0));

        rateAsCustomer(actors, appointment, value)
                .andExpect(status().isBadRequest());

        assertTrue(ratingRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId()).isEmpty());
    }

    @Test
    void customerCannotRateBeforeCompletionOrAnotherCustomersAppointment()
            throws Exception {
        TestActors owner = actors("401");
        TestActors other = actors("402");
        Appointment booked = bookedAppointment(owner, LocalTime.of(13, 0), true);

        rateAsCustomer(owner, booked, 4).andExpect(status().isBadRequest());

        booked.arrive();
        booked.complete();
        mockMvc.perform(post("/api/me/appointments/{id}/rating", booked.getId())
                        .session(sessionFor(other.customerUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotRateGuestAppointment() throws Exception {
        TestActors actors = actors("501");
        Appointment guest = bookedAppointment(actors, LocalTime.of(14, 0), false);
        guest.arrive();
        guest.complete();

        rateAsCustomer(actors, guest, 4).andExpect(status().isForbidden());
    }

    @Test
    void customerRatingRequiresAuthentication() throws Exception {
        TestActors actors = actors("502");
        Appointment appointment = completedAppointment(
                actors, LocalTime.of(14, 30));

        mockMvc.perform(post("/api/me/appointments/{id}/rating",
                        appointment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4}"))
                .andExpect(status().isUnauthorized());

        assertTrue(ratingRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId()).isEmpty());
    }

    @Test
    void customerCannotRateAnUnlinkedCustomerAppointmentEvenWithMatchingPhone()
            throws Exception {
        TestActors actors = actors("503");
        Customer unlinked = customerRepository.save(new Customer(
                "Legacy customer", actors.customerUser().getPhone()));
        Appointment appointment = completedAppointment(
                actors, unlinked, LocalTime.of(14, 45));

        rateAsCustomer(actors, appointment, 4)
                .andExpect(status().isForbidden());

        assertTrue(ratingRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId()).isEmpty());
    }

    @Test
    void unverifiedLinkedCustomerCannotSubmitCustomerRating() throws Exception {
        TestActors actors = actors("504");
        User unverifiedUser = userRepository.save(new User("+989125555041"));
        Customer unverified = new Customer(
                "Unverified", unverifiedUser.getPhone());
        ReflectionTestUtils.setField(unverified, "user", unverifiedUser);
        customerRepository.save(unverified);
        Appointment appointment = completedAppointment(
                actors, unverified, LocalTime.of(14, 50));

        mockMvc.perform(post("/api/me/appointments/{id}/rating",
                        appointment.getId())
                        .session(sessionFor(unverifiedUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4}"))
                .andExpect(status().isForbidden());

        assertTrue(ratingRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId()).isEmpty());
    }

    @Test
    void barberCanRateRegisteredCustomerOnceAfterCompletion() throws Exception {
        TestActors actors = actors("601");
        Appointment appointment = completedAppointment(actors, LocalTime.of(15, 0));

        rateAsBarber(actors, appointment, 5)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.raterType").value("BARBER"))
                .andExpect(jsonPath("$.rating").value(5));
        rateAsBarber(actors, appointment, 4)
                .andExpect(status().isConflict());

        List<AppointmentRating> ratings = ratingRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId());
        assertEquals(1, ratings.size());
        assertEquals(RatingRaterType.BARBER, ratings.getFirst().getRaterType());
    }

    @Test
    void barberCannotRateAnotherBarbersAppointment() throws Exception {
        TestActors owner = actors("701");
        TestActors other = actors("702");
        Appointment appointment = completedAppointment(owner, LocalTime.of(16, 0));

        mockMvc.perform(post("/api/me/barber/appointments/{id}/rating",
                        appointment.getId())
                        .session(sessionFor(other.barber().getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void barberCannotRateBeforeCompletionOrRateGuest() throws Exception {
        TestActors actors = actors("801");
        Appointment booked = bookedAppointment(actors, LocalTime.of(9, 0), true);
        Appointment guest = bookedAppointment(actors, LocalTime.of(9, 30), false);
        guest.arrive();
        guest.complete();

        rateAsBarber(actors, booked, 4).andExpect(status().isBadRequest());
        rateAsBarber(actors, guest, 4).andExpect(status().isForbidden());
    }

    @Test
    void neitherSideCanRateCompletedBarberManualGuestAppointment()
            throws Exception {
        TestActors actors = actors("803");
        Appointment appointment = Appointment.barberManualGuestBooking(
                actors.barber(), actors.offering(), "Manual guest",
                "+989128000803", LocalDate.of(2026, 10, 5),
                LocalTime.of(11, 0));
        appointment.arrive();
        appointment.complete();
        appointment = appointmentRepository.save(appointment);

        assertEquals(BookingSource.BARBER, appointment.getBookingSource());
        assertFalse(appointment.isCustomerAccepted());
        rateAsCustomer(actors, appointment, 4).andExpect(status().isForbidden());
        rateAsBarber(actors, appointment, 5).andExpect(status().isForbidden());
        assertTrue(ratingRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId()).isEmpty());
    }

    @Test
    void claimingCompletedBarberManualAppointmentDoesNotEnableRatings()
            throws Exception {
        TestActors actors = actors("0000804");
        Appointment appointment = Appointment.barberManualGuestBooking(
                actors.barber(), actors.offering(), "Claimable guest",
                actors.customerUser().getPhone(), LocalDate.of(2026, 10, 5),
                LocalTime.of(11, 30));
        appointment.arrive();
        appointment.complete();
        appointment = appointmentRepository.saveAndFlush(appointment);

        mockMvc.perform(post("/api/me/appointment-claims/{id}/confirm",
                        appointment.getId())
                        .session(sessionFor(actors.customerUser())))
                .andExpect(status().isNoContent());

        Appointment claimed = appointmentRepository.findById(appointment.getId())
                .orElseThrow();
        assertEquals(actors.customer().getId(), claimed.getCustomer().getId());
        assertEquals(BookingSource.BARBER, claimed.getBookingSource());
        assertFalse(claimed.isCustomerAccepted());
        rateAsCustomer(actors, claimed, 4).andExpect(status().isForbidden());
        rateAsBarber(actors, claimed, 5).andExpect(status().isForbidden());
        assertTrue(ratingRepository
                .findByAppointmentIdOrderByIdAsc(claimed.getId()).isEmpty());
    }

    @Test
    void historicallyAcceptedBarberBookingRemainsRateableByBothSides()
            throws Exception {
        TestActors actors = actors("805");
        Appointment appointment = new Appointment(
                actors.barber(), actors.offering(), actors.customer(),
                BookingSource.BARBER, LocalDate.of(2026, 10, 5),
                LocalTime.of(12, 30));
        appointment.confirmBooking();
        appointment.arrive();
        appointment.complete();
        appointment = appointmentRepository.saveAndFlush(appointment);

        assertEquals(BookingSource.BARBER, appointment.getBookingSource());
        assertTrue(appointment.isCustomerAccepted());
        rateAsCustomer(actors, appointment, 4).andExpect(status().isCreated());
        rateAsBarber(actors, appointment, 5).andExpect(status().isCreated());
        assertEquals(2, ratingRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId()).size());
    }

    @Test
    void barberCannotRateUnlinkedOrUnverifiedCustomer() throws Exception {
        TestActors actors = actors("802");
        Customer unlinked = customerRepository.save(new Customer(
                "Legacy", "+989125558020"));
        Appointment unlinkedAppointment = completedAppointment(
                actors, unlinked, LocalTime.of(9, 45));

        User unverifiedUser = userRepository.save(new User("+989125558021"));
        Customer unverified = new Customer(
                "Unverified", unverifiedUser.getPhone());
        ReflectionTestUtils.setField(unverified, "user", unverifiedUser);
        customerRepository.save(unverified);
        Appointment unverifiedAppointment = completedAppointment(
                actors, unverified, LocalTime.of(10, 30));

        rateAsBarber(actors, unlinkedAppointment, 4)
                .andExpect(status().isForbidden());
        rateAsBarber(actors, unverifiedAppointment, 4)
                .andExpect(status().isForbidden());

        assertTrue(ratingRepository.findAll().isEmpty());
    }

    @Test
    void twoSidesStoreIndependentRatingsWithoutChangingReputation()
            throws Exception {
        TestActors actors = actors("901");
        Appointment appointment = completedAppointment(actors, LocalTime.of(17, 0));
        reputationService.getOrCreateCustomerReputation(actors.customer());
        reputationService.getOrCreateBarberReputation(actors.barber());

        rateAsCustomer(actors, appointment, 4).andExpect(status().isCreated());
        rateAsBarber(actors, appointment, 5).andExpect(status().isCreated());

        List<AppointmentRating> ratings = ratingRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId());
        assertEquals(2, ratings.size());
        assertEquals(List.of(RatingRaterType.CUSTOMER, RatingRaterType.BARBER),
                ratings.stream().map(AppointmentRating::getRaterType).toList());
        assertEquals(100, customerReputationRepository
                .findByCustomerId(actors.customer().getId()).orElseThrow().getScore());
        assertEquals(100, barberReputationRepository
                .findByBarberId(actors.barber().getId()).orElseThrow().getScore());
    }

    private org.springframework.test.web.servlet.ResultActions rateAsCustomer(
            TestActors actors,
            Appointment appointment,
            int value
    ) throws Exception {
        return mockMvc.perform(post("/api/me/appointments/{id}/rating",
                        appointment.getId())
                .session(sessionFor(actors.customerUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":" + value + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions rateAsBarber(
            TestActors actors,
            Appointment appointment,
            int value
    ) throws Exception {
        return mockMvc.perform(post("/api/me/barber/appointments/{id}/rating",
                        appointment.getId())
                .session(sessionFor(actors.barber().getUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":" + value + "}"));
    }

    private Appointment completedAppointment(TestActors actors, LocalTime time) {
        Appointment appointment = bookedAppointment(actors, time, true);
        appointment.arrive();
        appointment.complete();
        return appointment;
    }

    private Appointment completedAppointment(
            TestActors actors,
            Customer customer,
            LocalTime time
    ) {
        Appointment appointment = appointmentRepository.save(new Appointment(
                actors.barber(), actors.offering(), customer,
                LocalDate.of(2026, 10, 5), time));
        appointment.arrive();
        appointment.complete();
        return appointment;
    }

    private Appointment bookedAppointment(
            TestActors actors,
            LocalTime time,
            boolean registered
    ) {
        Appointment appointment = registered
                ? new Appointment(actors.barber(), actors.offering(),
                        actors.customer(), LocalDate.of(2026, 10, 5), time)
                : new Appointment(actors.barber(), actors.offering(),
                        "Guest", "+989121234567",
                        LocalDate.of(2026, 10, 5), time);
        return appointmentRepository.save(appointment);
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
