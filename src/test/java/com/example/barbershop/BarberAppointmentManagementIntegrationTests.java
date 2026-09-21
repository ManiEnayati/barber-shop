package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentHistory;
import com.example.barbershop.entity.AppointmentHistoryAction;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.CancellationReason;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BarberAppointmentManagementIntegrationTests {

    private static final String BASE = "/api/me/barber/appointments/{id}/";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AppointmentHistoryRepository historyRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void barberCanArriveAndCompleteOwnAppointmentWithHistory() throws Exception {
        Barber barber = saveBarber("+989120000001");
        Appointment appointment = saveAppointment(barber);
        MockHttpSession session = sessionFor(barber.getUser());

        mockMvc.perform(patch(BASE + "arrive", appointment.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"));
        mockMvc.perform(patch(BASE + "complete", appointment.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertPersisted(appointment.getId(), AppointmentStatus.COMPLETED,
                List.of(AppointmentHistoryAction.CUSTOMER_ARRIVED,
                        AppointmentHistoryAction.APPOINTMENT_COMPLETED));
    }

    @Test
    void barberCanMarkOwnBookedAppointmentNoShow() throws Exception {
        Barber barber = saveBarber("+989120000002");
        Appointment appointment = saveAppointment(barber);

        mockMvc.perform(patch(BASE + "no-show", appointment.getId())
                        .session(sessionFor(barber.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));

        assertPersisted(appointment.getId(), AppointmentStatus.NO_SHOW,
                List.of(AppointmentHistoryAction.CUSTOMER_NO_SHOW));
    }

    @Test
    void barberCanCancelOwnBookedAppointmentWithReason() throws Exception {
        Barber barber = saveBarber("+989120000003");
        Appointment appointment = saveAppointment(barber);

        mockMvc.perform(patch(BASE + "cancel", appointment.getId())
                        .session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"emergency\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("BARBER_REQUEST"))
                .andExpect(jsonPath("$.cancellationNote").value("emergency"));

        assertPersisted(appointment.getId(), AppointmentStatus.CANCELLED,
                List.of(AppointmentHistoryAction.BARBER_CANCELLED));
        assertEquals(CancellationReason.BARBER_REQUEST,
                appointmentRepository.findById(appointment.getId()).orElseThrow()
                        .getCancellationReason());
        assertEquals("emergency", appointmentRepository.findById(appointment.getId())
                .orElseThrow().getCancellationNote());
        assertEquals("status=CANCELLED,reason=emergency", historyRepository
                .findByAppointmentIdOrderByIdAsc(appointment.getId()).getFirst().getNewValue());
    }

    @Test
    void barberCannotModifyAnotherBarbersAppointment() throws Exception {
        Barber ownBarber = saveBarber("+989120000004");
        Appointment otherAppointment = saveAppointment(saveBarber("+989120000005"));

        mockMvc.perform(patch(BASE + "arrive", otherAppointment.getId())
                        .session(sessionFor(ownBarber.getUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(BASE + "cancel", otherAppointment.getId())
                        .session(sessionFor(ownBarber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"emergency\"}"))
                .andExpect(status().isForbidden());

        assertPersisted(otherAppointment.getId(), AppointmentStatus.BOOKED, List.of());
    }

    @Test
    void customerCannotAccessBarberLifecycleEndpoints() throws Exception {
        User customer = userRepository.save(verifiedUser("+989120000006"));
        Barber barber = saveBarber("+989120000007");
        Appointment appointment = saveAppointment(barber);

        for (String action : List.of("arrive", "complete", "no-show", "cancel")) {
            mockMvc.perform(patch(BASE + action, appointment.getId())
                            .session(sessionFor(customer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"emergency\"}"))
                    .andExpect(status().isForbidden());
        }
        assertPersisted(appointment.getId(), AppointmentStatus.BOOKED, List.of());
    }

    @Test
    void missingAppointmentReturnsNotFound() throws Exception {
        Barber barber = saveBarber("+989120000008");

        mockMvc.perform(patch(BASE + "arrive", Long.MAX_VALUE)
                        .session(sessionFor(barber.getUser())))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidTransitionsDoNotChangeStatusOrHistory() throws Exception {
        Barber barber = saveBarber("+989120000009");
        Appointment appointment = saveAppointment(barber);
        MockHttpSession session = sessionFor(barber.getUser());

        mockMvc.perform(patch(BASE + "complete", appointment.getId()).session(session))
                .andExpect(status().isBadRequest());
        assertPersisted(appointment.getId(), AppointmentStatus.BOOKED, List.of());

        mockMvc.perform(patch(BASE + "arrive", appointment.getId()).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(patch(BASE + "arrive", appointment.getId()).session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(BASE + "no-show", appointment.getId()).session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(BASE + "cancel", appointment.getId()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"emergency\"}"))
                .andExpect(status().isBadRequest());
        assertPersisted(appointment.getId(), AppointmentStatus.ARRIVED,
                List.of(AppointmentHistoryAction.CUSTOMER_ARRIVED));
    }

    @Test
    void repeatedCompletionAndNoShowAreRejectedWithoutExtraHistory() throws Exception {
        Barber barber = saveBarber("+989120000011");
        MockHttpSession session = sessionFor(barber.getUser());
        Appointment completed = saveAppointment(barber);

        mockMvc.perform(patch(BASE + "arrive", completed.getId()).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(patch(BASE + "complete", completed.getId()).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(patch(BASE + "complete", completed.getId()).session(session))
                .andExpect(status().isBadRequest());
        assertPersisted(completed.getId(), AppointmentStatus.COMPLETED,
                List.of(AppointmentHistoryAction.CUSTOMER_ARRIVED,
                        AppointmentHistoryAction.APPOINTMENT_COMPLETED));

        Appointment noShow = saveAppointment(barber);
        mockMvc.perform(patch(BASE + "no-show", noShow.getId()).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(patch(BASE + "no-show", noShow.getId()).session(session))
                .andExpect(status().isBadRequest());
        assertPersisted(noShow.getId(), AppointmentStatus.NO_SHOW,
                List.of(AppointmentHistoryAction.CUSTOMER_NO_SHOW));
    }

    @Test
    void cancellationRequiresReasonAndLeavesAppointmentBooked() throws Exception {
        Barber barber = saveBarber("+989120000010");
        Appointment appointment = saveAppointment(barber);
        MockHttpSession session = sessionFor(barber.getUser());

        mockMvc.perform(patch(BASE + "cancel", appointment.getId()).session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(BASE + "cancel", appointment.getId()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(BASE + "cancel", appointment.getId()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        assertPersisted(appointment.getId(), AppointmentStatus.BOOKED, List.of());
        assertNull(appointmentRepository.findById(appointment.getId()).orElseThrow()
                .getCancellationReason());
    }

    private Barber saveBarber(String phone) {
        User user = verifiedUser(phone);
        user.approveBarber();
        userRepository.save(user);
        return barberRepository.save(new Barber(user, "Barber", LocalTime.of(10, 0),
                LocalTime.of(18, 0)));
    }

    private User verifiedUser(String phone) {
        User user = new User(phone);
        user.verifyPhone();
        return user;
    }

    private Appointment saveAppointment(Barber barber) {
        BarberServiceOffering offering = serviceRepository.save(new BarberServiceOffering(
                barber, "Haircut", 30, 400000L));
        Customer customer = customerRepository.save(new Customer("Customer", "09123334444"));
        return appointmentRepository.save(new Appointment(barber, offering, customer,
                LocalDate.of(2026, 9, 21), LocalTime.of(10, 0)));
    }

    private void assertPersisted(Long id, AppointmentStatus status,
                                 List<AppointmentHistoryAction> expectedActions) {
        appointmentRepository.flush();
        historyRepository.flush();
        entityManager.clear();
        assertEquals(status, appointmentRepository.findById(id).orElseThrow().getStatus());
        List<AppointmentHistory> history = historyRepository.findByAppointmentIdOrderByIdAsc(id);
        assertEquals(expectedActions, history.stream().map(AppointmentHistory::getAction).toList());
    }

    private MockHttpSession sessionFor(User user) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(user.getId()), null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context);
        return session;
    }
}
