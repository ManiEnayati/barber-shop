package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.repository.UserRepository;
import com.example.barbershop.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BarberDashboardIntegrationTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 25);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BlockedTimeRepository blockedTimeRepository;

    @Test
    void authenticatedBarberCanGetOwnProfile() throws Exception {
        Barber barber = saveBarber("+989121111111", "Ali Rezaei");

        mockMvc.perform(get("/api/me/barber")
                        .session(sessionFor(barber.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(barber.getId()))
                .andExpect(jsonPath("$.name").value("Ali Rezaei"))
                .andExpect(jsonPath("$.phone").value("+989121111111"))
                .andExpect(jsonPath("$.workStartTime").value("10:00:00"))
                .andExpect(jsonPath("$.workEndTime").value("18:00:00"));
    }

    @Test
    void customerWithoutBarberRoleReceivesForbidden() throws Exception {
        User customer = saveVerifiedUser("+989121111111");

        mockMvc.perform(get("/api/me/barber").session(sessionFor(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me/barber/calendar")
                        .param("date", DATE.toString())
                        .session(sessionFor(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me/barber/appointments")
                        .session(sessionFor(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileAlwaysComesFromAuthenticatedBarber() throws Exception {
        Barber ownBarber = saveBarber("+989121111111", "Own profile");
        Barber otherBarber = saveBarber("+989122222222", "Other profile");

        mockMvc.perform(get("/api/me/barber")
                        .param("barberId", otherBarber.getId().toString())
                        .param("userId", otherBarber.getUser().getId().toString())
                        .session(sessionFor(ownBarber.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownBarber.getId()))
                .andExpect(jsonPath("$.name").value("Own profile"))
                .andExpect(jsonPath("$.id").value(not(otherBarber.getId())));
    }

    @Test
    void calendarReturnsOnlyOwnScheduleAndBlockedPeriods() throws Exception {
        Barber barber = saveBarber("+989121111111", "Own profile");
        Barber otherBarber = saveBarber("+989122222222", "Other profile");
        BarberServiceOffering service = saveService(barber, 30);
        BarberServiceOffering otherService = saveService(otherBarber, 30);
        Customer customer = saveCustomer();
        Appointment ownAppointment = appointmentRepository.save(new Appointment(
                barber, service, customer, DATE, LocalTime.of(10, 0)
        ));
        appointmentRepository.save(new Appointment(
                otherBarber, otherService, customer, DATE, LocalTime.of(10, 30)
        ));
        blockedTimeRepository.save(new BlockedTime(
                barber,
                DATE,
                LocalTime.of(11, 0),
                LocalTime.of(11, 30),
                "Break"
        ));

        mockMvc.perform(get("/api/me/barber/calendar")
                        .param("date", DATE.toString())
                        .session(sessionFor(barber.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(DATE.toString()))
                .andExpect(jsonPath("$.workingStart").value("10:00:00"))
                .andExpect(jsonPath("$.workingEnd").value("18:00:00"))
                .andExpect(jsonPath("$.slots.length()").value(16))
                .andExpect(jsonPath("$.slots[0].time").value("10:00:00"))
                .andExpect(jsonPath("$.slots[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.slots[0].appointmentId")
                        .value(ownAppointment.getId()))
                .andExpect(jsonPath("$.slots[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.slots[1].appointmentId").doesNotExist())
                .andExpect(jsonPath("$.slots[2].status").value("BLOCKED"));
    }

    @Test
    void calendarMarksEveryBlockCoveredByAppointmentDuration() throws Exception {
        Barber barber = saveBarber("+989121111111", "Ali Rezaei");
        BarberServiceOffering service = saveService(barber, 60);
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber, service, saveCustomer(), DATE, LocalTime.of(10, 30)
        ));

        mockMvc.perform(get("/api/me/barber/calendar")
                        .param("date", DATE.toString())
                        .session(sessionFor(barber.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.slots[1].status").value("BOOKED"))
                .andExpect(jsonPath("$.slots[1].appointmentId")
                        .value(appointment.getId()))
                .andExpect(jsonPath("$.slots[2].status").value("BOOKED"))
                .andExpect(jsonPath("$.slots[2].appointmentId")
                        .value(appointment.getId()))
                .andExpect(jsonPath("$.slots[3].status").value("AVAILABLE"));
    }

    @Test
    void appointmentsReturnOnlyAuthenticatedBarbersAppointments() throws Exception {
        Barber barber = saveBarber("+989121111111", "Own profile");
        Barber otherBarber = saveBarber("+989122222222", "Other profile");
        Customer customer = saveCustomer();
        Appointment ownFirst = appointmentRepository.save(new Appointment(
                barber, saveService(barber, 30), customer,
                DATE, LocalTime.of(10, 0)
        ));
        Appointment ownSecond = appointmentRepository.save(new Appointment(
                barber, saveService(barber, 60), customer,
                DATE.plusDays(1), LocalTime.of(11, 0)
        ));
        appointmentRepository.save(new Appointment(
                otherBarber, saveService(otherBarber, 30), customer,
                DATE, LocalTime.of(12, 0)
        ));

        mockMvc.perform(get("/api/me/barber/appointments")
                        .session(sessionFor(barber.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", contains(
                        ownFirst.getId().intValue(), ownSecond.getId().intValue()
                )))
                .andExpect(jsonPath("$[*].barberId", contains(
                        barber.getId().intValue(), barber.getId().intValue()
                )));
    }

    @Test
    void appointmentsDateFilterReturnsOnlyRequestedDate() throws Exception {
        Barber barber = saveBarber("+989121111111", "Ali Rezaei");
        BarberServiceOffering service = saveService(barber, 30);
        Customer customer = saveCustomer();
        Appointment onDate = appointmentRepository.save(new Appointment(
                barber, service, customer, DATE, LocalTime.of(10, 0)
        ));
        appointmentRepository.save(new Appointment(
                barber, service, customer, DATE.plusDays(1), LocalTime.of(10, 0)
        ));

        mockMvc.perform(get("/api/me/barber/appointments")
                        .param("date", DATE.toString())
                        .session(sessionFor(barber.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(onDate.getId()))
                .andExpect(jsonPath("$[0].date").value(DATE.toString()));
    }

    @Test
    void appointmentsStatusFilterReturnsOnlyRequestedStatus() throws Exception {
        Barber barber = saveBarber("+989121111111", "Ali Rezaei");
        BarberServiceOffering service = saveService(barber, 30);
        Customer customer = saveCustomer();
        appointmentRepository.save(new Appointment(
                barber, service, customer, DATE, LocalTime.of(10, 0)
        ));
        Appointment cancelled = new Appointment(
                barber, service, customer, DATE, LocalTime.of(11, 0)
        );
        cancelled.cancel();
        appointmentRepository.save(cancelled);

        mockMvc.perform(get("/api/me/barber/appointments")
                        .param("status", "CANCELLED")
                        .session(sessionFor(barber.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(cancelled.getId()))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    void unauthenticatedDashboardRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/me/barber"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/barber/calendar")
                        .param("date", DATE.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/barber/appointments"))
                .andExpect(status().isUnauthorized());
    }

    private Barber saveBarber(String phone, String name) {
        User user = saveVerifiedUser(phone);
        user.approveBarber();
        userRepository.save(user);
        return barberRepository.save(new Barber(
                user,
                name,
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
    }

    private User saveVerifiedUser(String phone) {
        User user = new User(phone);
        user.verifyPhone();
        return userRepository.save(user);
    }

    private BarberServiceOffering saveService(Barber barber, int durationMinutes) {
        return serviceRepository.save(new BarberServiceOffering(
                barber,
                "Service " + durationMinutes,
                durationMinutes,
                400000L
        ));
    }

    private Customer saveCustomer() {
        return customerRepository.save(new Customer(
                "Reza Karimi",
                "+989123333333"
        ));
    }

    private MockHttpSession sessionFor(User user) {
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(user.getId()),
                null,
                authorities
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
}
