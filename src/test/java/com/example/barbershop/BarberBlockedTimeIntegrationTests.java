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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BarberBlockedTimeIntegrationTests {

    private static final String PATH = "/api/me/barber/blocked-times";
    private static final LocalDate DATE = LocalDate.of(2026, 9, 25);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository offeringRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private BlockedTimeRepository blockedTimeRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void barberCreatesOwnBlockAndCreationTimeIsStored() throws Exception {
        Barber barber = saveBarber("+989120001001");
        Barber other = saveBarber("+989120001002");

        mockMvc.perform(post(PATH).session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("14:00", "15:30", "personal")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.barberId").value(barber.getId()))
                .andExpect(jsonPath("$.date").value(DATE.toString()))
                .andExpect(jsonPath("$.startTime").value("14:00:00"))
                .andExpect(jsonPath("$.endTime").value("15:30:00"))
                .andExpect(jsonPath("$.reason").value("personal"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        blockedTimeRepository.flush();
        entityManager.clear();
        BlockedTime stored = blockedTimeRepository.findByBarberIdAndDate(barber.getId(), DATE)
                .getFirst();
        assertEquals(barber.getId(), stored.getBarber().getId());
        assertEquals("personal", stored.getReason());
        assertNotNull(stored.getCreatedAt());
        assertEquals(0, blockedTimeRepository.findByBarberIdAndDate(other.getId(), DATE).size());
    }

    @Test
    void listReturnsOnlyOwnBlocksOnRequestedDate() throws Exception {
        Barber barber = saveBarber("+989120001003");
        Barber other = saveBarber("+989120001004");
        BlockedTime own = saveBlock(barber, DATE, "14:00", "15:00");
        saveBlock(barber, DATE.plusDays(1), "14:00", "15:00");
        saveBlock(other, DATE, "14:00", "15:00");

        mockMvc.perform(get(PATH).session(sessionFor(barber.getUser()))
                        .param("date", DATE.toString())
                        .param("barberId", other.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(own.getId()));
    }

    @Test
    void customerAndUnauthenticatedUserCannotManageBlocks() throws Exception {
        User customer = new User("+989120001005");
        customer.verifyPhone();
        userRepository.save(customer);
        Barber barber = saveBarber("+989120001006");
        BlockedTime block = saveBlock(barber, DATE, "14:00", "15:00");
        MockHttpSession session = sessionFor(customer);

        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("15:00", "16:00", "personal")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).session(session).param("date", DATE.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(PATH + "/{id}", block.getId()).session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).param("date", DATE.toString()))
                .andExpect(status().isUnauthorized());
        assertTrue(blockedTimeRepository.existsById(block.getId()));
    }

    @Test
    void anotherBarberCannotDeleteBlock() throws Exception {
        Barber owner = saveBarber("+989120001007");
        Barber other = saveBarber("+989120001008");
        BlockedTime block = saveBlock(owner, DATE, "14:00", "15:00");

        mockMvc.perform(delete(PATH + "/{id}", block.getId())
                        .session(sessionFor(other.getUser())))
                .andExpect(status().isForbidden());

        assertTrue(blockedTimeRepository.existsById(block.getId()));
    }

    @Test
    void invalidTimeRangeAndMissingReasonAreRejected() throws Exception {
        Barber barber = saveBarber("+989120001009");
        MockHttpSession session = sessionFor(barber.getUser());

        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("15:00", "14:00", "personal")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("14:00", "14:00", "personal")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("14:00", "15:00", " ")))
                .andExpect(status().isBadRequest());
        assertEquals(0, blockedTimeRepository.count());
    }

    @Test
    void blockOutsideWorkingHoursIsRejected() throws Exception {
        Barber barber = saveBarber("+989120001010");
        MockHttpSession session = sessionFor(barber.getUser());

        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("09:30", "10:30", "personal")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("17:30", "18:30", "personal")))
                .andExpect(status().isBadRequest());
        assertEquals(0, blockedTimeRepository.count());
    }

    @Test
    void blockOverlappingAppointmentIsRejected() throws Exception {
        Barber barber = saveBarber("+989120001011");
        BarberServiceOffering offering = offeringRepository.save(new BarberServiceOffering(
                barber, "Haircut", 60, 400000L));
        Customer customer = customerRepository.save(new Customer("Customer", "09123334444"));
        appointmentRepository.save(new Appointment(barber, offering, customer, DATE,
                LocalTime.of(14, 0)));

        mockMvc.perform(post(PATH).session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("14:30", "15:30", "personal")))
                .andExpect(status().isConflict());
        assertEquals(0, blockedTimeRepository.count());
    }

    @Test
    void blockOverlappingAnotherBlockIsRejected() throws Exception {
        Barber barber = saveBarber("+989120001012");
        saveBlock(barber, DATE, "14:00", "15:00");

        mockMvc.perform(post(PATH).session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("14:30", "15:30", "personal")))
                .andExpect(status().isConflict());
        assertEquals(1, blockedTimeRepository.count());
    }

    @Test
    void calendarShowsPersonalBlockAndDeleteReopensSlots() throws Exception {
        Barber barber = saveBarber("+989120001013");
        MockHttpSession session = sessionFor(barber.getUser());

        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("14:00", "15:30", "personal")))
                .andExpect(status().isCreated());
        Long blockId = blockedTimeRepository.findByBarberIdAndDate(barber.getId(), DATE)
                .getFirst().getId();

        mockMvc.perform(get("/api/me/barber/calendar").session(session)
                        .param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[7].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.slots[8].status").value("BLOCKED"))
                .andExpect(jsonPath("$.slots[9].status").value("BLOCKED"))
                .andExpect(jsonPath("$.slots[10].status").value("BLOCKED"))
                .andExpect(jsonPath("$.slots[11].status").value("AVAILABLE"));

        mockMvc.perform(delete(PATH + "/{id}", blockId).session(session))
                .andExpect(status().isNoContent());
        assertFalse(blockedTimeRepository.existsById(blockId));
        mockMvc.perform(get("/api/me/barber/calendar").session(session)
                        .param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[8].status").value("AVAILABLE"));
    }

    private Barber saveBarber(String phone) {
        User user = new User(phone);
        user.verifyPhone();
        user.approveBarber();
        userRepository.save(user);
        return barberRepository.save(new Barber(user, "Barber", LocalTime.of(10, 0),
                LocalTime.of(18, 0)));
    }

    private BlockedTime saveBlock(Barber barber, LocalDate date, String start, String end) {
        return blockedTimeRepository.save(new BlockedTime(barber, date,
                LocalTime.parse(start), LocalTime.parse(end), "personal"));
    }

    private String blockJson(String start, String end, String reason) {
        return "{\"date\":\"" + DATE + "\",\"startTime\":\"" + start
                + "\",\"endTime\":\"" + end + "\",\"reason\":\"" + reason
                + "\"}";
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
