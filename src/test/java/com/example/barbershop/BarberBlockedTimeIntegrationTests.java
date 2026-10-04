package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BarberWeeklySchedule;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.BookingSource;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.entity.User;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.BarberWeeklyScheduleRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired private BarberWeeklyScheduleRepository weeklyScheduleRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void legacyPublicWritesAreRemovedForAnonymousAndAuthenticatedCallers()
            throws Exception {
        Barber barber = saveBarber("+989120001032");
        BlockedTime block = saveBlock(barber, DATE, "14:00", "15:00");
        String legacyBody = "{\"barberId\":" + barber.getId()
                + ",\"date\":\"" + DATE
                + "\",\"startTime\":\"15:00\",\"endTime\":\"16:00\"}";

        mockMvc.perform(post("/api/blocked-times")
                        .contentType(MediaType.APPLICATION_JSON).content(legacyBody))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/blocked-times")
                        .session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(legacyBody))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/blocked-times/{id}", block.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/blocked-times/{id}", block.getId())
                        .session(sessionFor(barber.getUser())))
                .andExpect(status().isNotFound());

        assertTrue(blockedTimeRepository.existsById(block.getId()));
        assertEquals(1, blockedTimeRepository.count());
    }

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
        mockMvc.perform(post(PATH + "/bulk").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + blockJson("15:00", "16:00", null) + "]"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH + "/week").session(session)
                        .param("startDate", DATE.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(PATH + "/{id}", block.getId()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("15:00", "16:00", null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).param("date", DATE.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH + "/bulk").contentType(MediaType.APPLICATION_JSON)
                        .content("[" + blockJson("15:00", "16:00", null) + "]"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH + "/week").param("startDate", DATE.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(PATH + "/{id}", block.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("15:00", "16:00", null)))
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
    void invalidTimeRangeIsRejected() throws Exception {
        Barber barber = saveBarber("+989120001009");
        MockHttpSession session = sessionFor(barber.getUser());

        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("15:00", "14:00", "personal")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson("14:00", "14:00", "personal")))
                .andExpect(status().isBadRequest());
        assertEquals(0, blockedTimeRepository.count());
    }

    @Test
    void optionalAndBlankReasonsAreStoredAsNullAndPresentReasonIsTrimmed() throws Exception {
        Barber barber = saveBarber("+989120001014");
        MockHttpSession session = sessionFor(barber.getUser());

        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "14:00", "15:00", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "15:00", "16:00", "  ")))
                .andExpect(status().isCreated());
        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "16:00", "17:00", " personal ")))
                .andExpect(status().isCreated());

        var blocks = blockedTimeRepository.findByBarberIdAndDate(barber.getId(), DATE);
        assertEquals(3, blocks.size());
        assertEquals(2, blocks.stream().filter(block -> block.getReason() == null).count());
        assertEquals("personal", blocks.stream().filter(block -> block.getReason() != null)
                .findFirst().orElseThrow().getReason());
    }

    @Test
    void reasonOver255CharactersIsRejected() throws Exception {
        Barber barber = saveBarber("+989120001015");
        mockMvc.perform(post(PATH).session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "14:00", "15:00", "a".repeat(256))))
                .andExpect(status().isBadRequest());
        assertEquals(0, blockedTimeRepository.count());
    }

    @Test
    void bulkCreateSavesAllDateSpecificBlocks() throws Exception {
        Barber barber = saveBarber("+989120001016");
        String body = "[" + blockJson(DATE, "14:00", "15:00", null) + ","
                + blockJson(DATE.plusDays(1), "14:00", "15:00", " personal ") + "]";

        mockMvc.perform(post(PATH + "/bulk").session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].barberId").value(barber.getId()))
                .andExpect(jsonPath("$[1].reason").value("personal"));
        assertEquals(2, blockedTimeRepository.count());
    }

    @Test
    void bulkRejectsInvalidItemWithoutSavingEarlierItems() throws Exception {
        Barber barber = saveBarber("+989120001017");
        String body = "[" + blockJson(DATE, "14:00", "15:00", null) + ","
                + blockJson(DATE, "18:00", "19:00", null) + "]";

        mockMvc.perform(post(PATH + "/bulk").session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, blockedTimeRepository.count());
    }

    @Test
    void bulkRejectsInternalOverlapButAcceptsTouchingBlocks() throws Exception {
        Barber barber = saveBarber("+989120001018");
        MockHttpSession session = sessionFor(barber.getUser());
        String overlapping = "[" + blockJson(DATE, "14:00", "15:00", null) + ","
                + blockJson(DATE, "14:30", "16:00", null) + "]";
        String touching = "[" + blockJson(DATE, "14:00", "15:00", null) + ","
                + blockJson(DATE, "15:00", "16:00", null) + "]";

        mockMvc.perform(post(PATH + "/bulk").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(overlapping))
                .andExpect(status().isConflict());
        assertEquals(0, blockedTimeRepository.count());
        mockMvc.perform(post(PATH + "/bulk").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(touching))
                .andExpect(status().isCreated());
        assertEquals(2, blockedTimeRepository.count());
    }

    @Test
    void bulkRejectsExistingConflictWithoutSavingOtherItems() throws Exception {
        Barber barber = saveBarber("+989120001028");
        saveBlock(barber, DATE, "16:00", "17:00");
        String body = "[" + blockJson(DATE, "14:00", "15:00", null) + ","
                + blockJson(DATE, "16:30", "17:30", null) + "]";

        mockMvc.perform(post(PATH + "/bulk").session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        assertEquals(1, blockedTimeRepository.count());
    }

    @Test
    void weekViewIsSortedAndRestrictedToOwnBlocksInSevenDayRange() throws Exception {
        Barber barber = saveBarber("+989120001019");
        Barber other = saveBarber("+989120001020");
        BlockedTime first = saveBlock(barber, DATE, "11:00", "12:00");
        BlockedTime second = saveBlock(barber, DATE, "10:00", "11:00");
        BlockedTime last = saveBlock(barber, DATE.plusDays(6), "14:00", "15:00");
        saveBlock(barber, DATE.plusDays(7), "14:00", "15:00");
        saveBlock(other, DATE, "14:00", "15:00");

        mockMvc.perform(get(PATH + "/week").session(sessionFor(barber.getUser()))
                        .param("startDate", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[1].id").value(first.getId()))
                .andExpect(jsonPath("$[2].id").value(last.getId()));
    }

    @Test
    void updateMovesAndResizesBlockAndCalendarReflectsNewPeriod() throws Exception {
        Barber barber = saveBarber("+989120001021");
        MockHttpSession session = sessionFor(barber.getUser());
        BlockedTime block = saveBlock(barber, DATE, "14:00", "15:00");
        var createdAt = block.getCreatedAt();

        mockMvc.perform(put(PATH + "/{id}", block.getId()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "15:00", "16:30", " changed ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("15:00:00"))
                .andExpect(jsonPath("$.endTime").value("16:30:00"))
                .andExpect(jsonPath("$.reason").value("changed"));
        mockMvc.perform(get("/api/me/barber/calendar").session(session)
                        .param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[8].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.slots[10].status").value("BLOCKED"))
                .andExpect(jsonPath("$.slots[12].status").value("BLOCKED"))
                .andExpect(jsonPath("$.slots[13].status").value("AVAILABLE"));
        assertEquals(createdAt, blockedTimeRepository.findById(block.getId())
                .orElseThrow().getCreatedAt());
    }

    @Test
    void updateCanMoveBlockToAnotherDate() throws Exception {
        Barber barber = saveBarber("+989120001029");
        MockHttpSession session = sessionFor(barber.getUser());
        BlockedTime block = saveBlock(barber, DATE, "14:00", "15:00");
        LocalDate nextDate = DATE.plusDays(1);

        mockMvc.perform(put(PATH + "/{id}", block.getId()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(nextDate, "14:00", "15:30", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(nextDate.toString()));
        mockMvc.perform(get("/api/me/barber/calendar").session(session)
                        .param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[8].status").value("AVAILABLE"));
        mockMvc.perform(get("/api/me/barber/calendar").session(session)
                        .param("date", nextDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[8].status").value("BLOCKED"));
    }

    @Test
    void anotherBarberCannotUpdateBlock() throws Exception {
        Barber owner = saveBarber("+989120001022");
        Barber other = saveBarber("+989120001023");
        BlockedTime block = saveBlock(owner, DATE, "14:00", "15:00");

        mockMvc.perform(put(PATH + "/{id}", block.getId())
                        .session(sessionFor(other.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "15:00", "16:00", null)))
                .andExpect(status().isForbidden());
        assertEquals(LocalTime.of(14, 0), blockedTimeRepository.findById(block.getId())
                .orElseThrow().getStartTime());
    }

    @Test
    void updateRejectsOverlapAndPreservesOriginalBlock() throws Exception {
        Barber barber = saveBarber("+989120001024");
        BlockedTime block = saveBlock(barber, DATE, "14:00", "15:00");
        saveBlock(barber, DATE, "16:00", "17:00");

        mockMvc.perform(put(PATH + "/{id}", block.getId())
                        .session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "15:30", "16:30", null)))
                .andExpect(status().isConflict());
        assertEquals(LocalTime.of(14, 0), blockedTimeRepository.findById(block.getId())
                .orElseThrow().getStartTime());
    }

    @Test
    void updateRejectsActiveAppointmentAndPreservesOriginalBlock() throws Exception {
        Barber barber = saveBarber("+989120001031");
        BlockedTime block = saveBlock(barber, DATE, "14:00", "15:00");
        BarberServiceOffering offering = offeringRepository.save(new BarberServiceOffering(
                barber, "Haircut", 60, 400000L));
        Customer customer = customerRepository.save(new Customer("Customer", "09123334447"));
        appointmentRepository.save(new Appointment(barber, offering, customer, DATE,
                LocalTime.of(15, 0)));

        mockMvc.perform(put(PATH + "/{id}", block.getId())
                        .session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "15:00", "16:00", null)))
                .andExpect(status().isConflict());
        assertEquals(LocalTime.of(14, 0), blockedTimeRepository.findById(block.getId())
                .orElseThrow().getStartTime());
    }

    @Test
    void inactiveWeeklyDayAndOutsideWeeklyHoursRejectBlocks() throws Exception {
        Barber barber = saveBarber("+989120001025");
        MockHttpSession session = sessionFor(barber.getUser());
        weeklyScheduleRepository.save(new BarberWeeklySchedule(barber, DayOfWeek.FRIDAY,
                null, null, false));
        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "14:00", "15:00", null)))
                .andExpect(status().isBadRequest());
        weeklyScheduleRepository.findByBarberId(barber.getId()).getFirst()
                .update(LocalTime.of(11, 0), LocalTime.of(17, 0), true);
        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "10:00", "11:30", null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "16:30", "17:30", null)))
                .andExpect(status().isBadRequest());
        assertEquals(0, blockedTimeRepository.count());
    }

    @Test
    void activeAppointmentsBlockButFinishedOrCancelledAppointmentsDoNot() throws Exception {
        Barber barber = saveBarber("+989120001026");
        BarberServiceOffering offering = offeringRepository.save(new BarberServiceOffering(
                barber, "Haircut", 60, 400000L));
        Customer customer = customerRepository.save(new Customer("Customer", "09123334445"));
        Appointment booked = appointmentRepository.save(new Appointment(barber, offering,
                customer, DATE, LocalTime.of(10, 0)));
        Appointment arrived = appointmentRepository.save(new Appointment(barber, offering,
                customer, DATE, LocalTime.of(11, 0)));
        arrived.arrive();
        Appointment cancelled = appointmentRepository.save(new Appointment(barber, offering,
                customer, DATE, LocalTime.of(12, 0)));
        cancelled.cancel();
        Appointment completed = appointmentRepository.save(new Appointment(barber, offering,
                customer, DATE, LocalTime.of(13, 0)));
        completed.arrive();
        completed.complete();
        Appointment noShow = appointmentRepository.save(new Appointment(barber, offering,
                customer, DATE, LocalTime.of(14, 0)));
        noShow.markNoShow();
        MockHttpSession session = sessionFor(barber.getUser());

        for (String start : new String[]{"10:00", "11:00"}) {
            mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                            .content(blockJson(DATE, start, LocalTime.parse(start)
                                    .plusHours(1).toString(), null)))
                    .andExpect(status().isConflict());
        }
        for (String start : new String[]{"12:00", "13:00", "14:00"}) {
            mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                            .content(blockJson(DATE, start, LocalTime.parse(start)
                                    .plusHours(1).toString(), null)))
                    .andExpect(status().isCreated());
        }
        assertEquals(3, blockedTimeRepository.count());
    }

    @Test
    void expiredAndRejectedConfirmationsDoNotBlockAvailability() throws Exception {
        Barber barber = saveBarber("+989120001030");
        BarberServiceOffering offering = offeringRepository.save(new BarberServiceOffering(
                barber, "Haircut", 60, 400000L));
        Customer customer = customerRepository.save(new Customer("Customer", "09123334446"));
        Appointment expired = appointmentRepository.save(new Appointment(barber, offering,
                customer, BookingSource.BARBER, DATE, LocalTime.of(14, 0)));
        expired.expireBooking();
        Appointment rejected = appointmentRepository.save(new Appointment(barber, offering,
                customer, BookingSource.BARBER, DATE, LocalTime.of(15, 0)));
        rejected.rejectBooking();
        MockHttpSession session = sessionFor(barber.getUser());

        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "14:00", "15:00", null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(PATH).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "15:00", "16:00", null)))
                .andExpect(status().isCreated());
    }

    @Test
    void unalignedTimesAreRejected() throws Exception {
        Barber barber = saveBarber("+989120001027");
        mockMvc.perform(post(PATH).session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(DATE, "14:15", "15:00", null)))
                .andExpect(status().isBadRequest());
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
        weeklyScheduleRepository.save(new BarberWeeklySchedule(barber, DayOfWeek.FRIDAY,
                LocalTime.of(10, 0), LocalTime.of(18, 0), true));

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
        return blockJson(DATE, start, end, reason);
    }

    private String blockJson(LocalDate date, String start, String end, String reason) {
        return "{\"date\":\"" + date + "\",\"startTime\":\"" + start
                + "\",\"endTime\":\"" + end + "\""
                + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
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
