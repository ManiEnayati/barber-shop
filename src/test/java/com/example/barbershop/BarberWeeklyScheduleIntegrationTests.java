package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.BarberWeeklyScheduleRepository;
import com.example.barbershop.repository.CustomerRepository;
import com.example.barbershop.repository.UserRepository;
import com.example.barbershop.security.AuthenticatedUser;
import com.example.barbershop.service.BarberScheduleService;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BarberWeeklyScheduleIntegrationTests {

    private static final String PATH = "/api/me/barber/schedule";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberWeeklyScheduleRepository scheduleRepository;
    @Autowired private BarberScheduleService scheduleService;
    @Autowired private BarberServiceOfferingRepository offeringRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private CustomerRepository customerRepository;

    @Test
    void barberViewsOnlyOwnDefaultSchedule() throws Exception {
        Barber barber = saveBarber("+989120002001");
        Barber other = saveBarber("+989120002002");
        scheduleService.initializeDefaultSchedule(barber);
        scheduleService.initializeDefaultSchedule(other);

        mockMvc.perform(get(PATH).session(sessionFor(barber.getUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].day").value("SATURDAY"))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("18:00:00"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$[6].day").value("FRIDAY"))
                .andExpect(jsonPath("$[6].active").value(false));
        assertEquals(7, scheduleRepository.findByBarberId(barber.getId()).size());
        LocalDate friday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        mockMvc.perform(get("/api/me/barber/calendar")
                        .session(sessionFor(barber.getUser()))
                        .param("date", friday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingStart").isEmpty())
                .andExpect(jsonPath("$.slots.length()").value(0));
    }

    @Test
    void barberReplacesFullScheduleAndMissingDaysBecomeInactive() throws Exception {
        Barber barber = saveBarber("+989120002003");
        scheduleService.initializeDefaultSchedule(barber);

        mockMvc.perform(put(PATH).session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"day":"SATURDAY","startTime":"12:00","endTime":"16:00","active":true},
                                  {"day":"SUNDAY","active":false}
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].startTime").value("12:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("16:00:00"))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].startTime").isEmpty())
                .andExpect(jsonPath("$[2].active").value(false));

        assertEquals(7, scheduleRepository.findByBarberId(barber.getId()).size());
        assertFalse(scheduleRepository.findByBarberIdAndDayOfWeek(barber.getId(),
                DayOfWeek.SUNDAY).orElseThrow().isActive());
    }

    @Test
    void customerAndUnauthenticatedUserCannotAccessSchedule() throws Exception {
        User customer = new User("+989120002004");
        customer.verifyPhone();
        userRepository.save(customer);
        MockHttpSession session = sessionFor(customer);

        mockMvc.perform(get(PATH).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(put(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateDayIsRejectedWithoutChangingSchedule() throws Exception {
        Barber barber = saveBarber("+989120002005");
        scheduleService.initializeDefaultSchedule(barber);

        mockMvc.perform(put(PATH).session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"day":"SATURDAY","startTime":"12:00","endTime":"16:00","active":true},
                                  {"day":"SATURDAY","active":false}
                                ]
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(LocalTime.of(10, 0), scheduleRepository
                .findByBarberIdAndDayOfWeek(barber.getId(), DayOfWeek.SATURDAY)
                .orElseThrow().getStartTime());
    }

    @Test
    void invalidTimesAreRejectedWithoutChangingSchedule() throws Exception {
        Barber barber = saveBarber("+989120002006");
        scheduleService.initializeDefaultSchedule(barber);
        MockHttpSession session = sessionFor(barber.getUser());

        for (String times : new String[]{
                "\"startTime\":\"16:00\",\"endTime\":\"12:00\"",
                "\"startTime\":\"10:15\",\"endTime\":\"18:00\"",
                "\"startTime\":\"10:00\""}) {
            mockMvc.perform(put(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"day\":\"SATURDAY\",\"active\":true," + times + "}]"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(LocalTime.of(10, 0), scheduleRepository
                .findByBarberIdAndDayOfWeek(barber.getId(), DayOfWeek.SATURDAY)
                .orElseThrow().getStartTime());
    }

    @Test
    void inactiveDayNeedsNoTimesAndHasNoCalendarSlots() throws Exception {
        Barber barber = saveBarber("+989120002007");
        scheduleService.initializeDefaultSchedule(barber);
        LocalDate saturday = nextSaturday();

        mockMvc.perform(put(PATH).session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"day\":\"SATURDAY\",\"active\":false}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startTime").isEmpty());
        mockMvc.perform(get("/api/me/barber/calendar")
                        .session(sessionFor(barber.getUser()))
                        .param("date", saturday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingStart").isEmpty())
                .andExpect(jsonPath("$.slots.length()").value(0));
    }

    @Test
    void futureAppointmentPreventsScheduleThatExcludesIt() throws Exception {
        Barber barber = saveBarber("+989120002008");
        scheduleService.initializeDefaultSchedule(barber);
        BarberServiceOffering offering = saveOffering(barber);
        Customer customer = customerRepository.save(new Customer("Customer", "09123334444"));
        appointmentRepository.save(new Appointment(barber, offering, customer,
                nextSaturday(), LocalTime.of(10, 30)));

        mockMvc.perform(put(PATH).session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"day":"SATURDAY","startTime":"12:00","endTime":"18:00","active":true}]
                                """))
                .andExpect(status().isConflict());

        assertEquals(LocalTime.of(10, 0), scheduleRepository
                .findByBarberIdAndDayOfWeek(barber.getId(), DayOfWeek.SATURDAY)
                .orElseThrow().getStartTime());
    }

    @Test
    void calendarAndAvailabilityUseWeeklyHoursInsteadOfLegacyHours() throws Exception {
        Barber barber = saveBarber("+989120002009");
        scheduleService.initializeDefaultSchedule(barber);
        BarberServiceOffering offering = saveOffering(barber);
        LocalDate saturday = nextSaturday();
        MockHttpSession session = sessionFor(barber.getUser());

        mockMvc.perform(put(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"day":"SATURDAY","startTime":"12:00","endTime":"16:00","active":true}]
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/barber/calendar").session(session)
                        .param("date", saturday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingStart").value("12:00:00"))
                .andExpect(jsonPath("$.workingEnd").value("16:00:00"))
                .andExpect(jsonPath("$.slots.length()").value(8))
                .andExpect(jsonPath("$.slots[0].time").value("12:00:00"));
        mockMvc.perform(get("/api/barbers/{id}/available-times", barber.getId())
                        .param("date", saturday.toString())
                        .param("serviceId", offering.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startTime").value("12:00:00"))
                .andExpect(jsonPath("$.length()").value(8));
        mockMvc.perform(post("/api/me/barber/blocked-times").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"%s","startTime":"10:00","endTime":"11:00","reason":"personal"}
                                """.formatted(saturday)))
                .andExpect(status().isBadRequest());
    }

    private Barber saveBarber(String phone) {
        User user = new User(phone);
        user.verifyPhone();
        user.approveBarber();
        userRepository.save(user);
        return barberRepository.save(new Barber(user, "Barber", LocalTime.of(10, 0),
                LocalTime.of(18, 0)));
    }

    private BarberServiceOffering saveOffering(Barber barber) {
        return offeringRepository.save(new BarberServiceOffering(barber, "Haircut", 30,
                400000L));
    }

    private LocalDate nextSaturday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
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
