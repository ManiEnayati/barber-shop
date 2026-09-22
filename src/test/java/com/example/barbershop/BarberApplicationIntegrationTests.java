package com.example.barbershop;

import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberApplication;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.repository.BarberApplicationRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberWeeklyScheduleRepository;
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

import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BarberApplicationIntegrationTests {

    private static final String CUSTOMER_PHONE = "+989121234567";
    private static final String ADMIN_PHONE = "+989001111111";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberWeeklyScheduleRepository weeklyScheduleRepository;
    @Autowired private BarberApplicationRepository applicationRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void verifiedCustomerSubmitsUsingOnlyAuthenticatedIdentity() throws Exception {
        User customer = saveVerifiedUser(CUSTOMER_PHONE);

        mockMvc.perform(post("/api/me/barber-application")
                        .session(sessionFor(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Ali Rezaei  ",
                                  "workStartTime": "10:00",
                                  "workEndTime": "18:00",
                                  "userId": 999,
                                  "phone": "+989999999999",
                                  "role": "BARBER",
                                  "status": "APPROVED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Ali Rezaei"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty())
                .andExpect(jsonPath("$.reviewedAt").isEmpty())
                .andExpect(jsonPath("$.reviewNote").isEmpty())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());

        BarberApplication application = applicationRepository.findAll().getFirst();
        User unchanged = userRepository.findById(customer.getId()).orElseThrow();
        assertEquals(customer.getId(), application.getUser().getId());
        assertEquals(CUSTOMER_PHONE, application.getUser().getPhone());
        assertEquals(Set.of(UserRole.CUSTOMER), unchanged.getRoles());
        assertEquals(0, barberRepository.count());
    }

    @Test
    void submissionRequiresAuthenticationAndValidSchedule() throws Exception {
        mockMvc.perform(post("/api/me/barber-application")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validApplicationJson("Ali")))
                .andExpect(status().isUnauthorized());

        User customer = saveVerifiedUser(CUSTOMER_PHONE);
        mockMvc.perform(post("/api/me/barber-application")
                        .session(sessionFor(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Ali",
                                  "workStartTime": "10:15",
                                  "workEndTime": "18:00"
                                }
                                """))
                .andExpect(status().isBadRequest());
        assertEquals(0, applicationRepository.count());
    }

    @Test
    void secondPendingApplicationIsRejectedAndExistingBarberCannotApply()
            throws Exception {
        User customer = saveVerifiedUser(CUSTOMER_PHONE);
        MockHttpSession session = sessionFor(customer);
        submit(session, "First").andExpect(status().isCreated());
        submit(session, "Second").andExpect(status().isConflict());

        User barberUser = saveVerifiedUser("+989121111111");
        barberUser.approveBarber();
        userRepository.save(barberUser);
        submit(sessionFor(barberUser), "Already barber")
                .andExpect(status().isConflict());
        assertEquals(1, applicationRepository.count());
    }

    @Test
    void rejectedCustomerMaySubmitNewApplicationAndSeesOwnNewestFirst()
            throws Exception {
        User customer = saveVerifiedUser(CUSTOMER_PHONE);
        User admin = saveAdmin();
        submit(sessionFor(customer), "First").andExpect(status().isCreated());
        BarberApplication first = applicationRepository.findAll().getFirst();
        reject(sessionFor(admin), first.getId(), "Identity could not be verified")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote")
                        .value("Identity could not be verified"));

        submit(sessionFor(customer), "Second").andExpect(status().isCreated());
        mockMvc.perform(get("/api/me/barber-applications")
                        .session(sessionFor(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Second", "First")))
                .andExpect(jsonPath("$[*].status", contains("PENDING", "REJECTED")));
    }

    @Test
    void meHistoryNeverReturnsAnotherUsersApplications() throws Exception {
        User first = saveVerifiedUser(CUSTOMER_PHONE);
        User second = saveVerifiedUser("+989121111111");
        submit(sessionFor(first), "First user").andExpect(status().isCreated());
        submit(sessionFor(second), "Second user").andExpect(status().isCreated());

        mockMvc.perform(get("/api/me/barber-applications")
                        .session(sessionFor(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("First user"));
    }

    @Test
    void adminListIsProtectedAndCanFilterPendingApplications() throws Exception {
        User customer = saveVerifiedUser(CUSTOMER_PHONE);
        User admin = saveAdmin();
        submit(sessionFor(customer), "Pending barber").andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/barber-applications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/barber-applications")
                        .session(sessionFor(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/barber-applications")
                        .param("status", "PENDING")
                        .session(sessionFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(customer.getId()))
                .andExpect(jsonPath("$[0].phone").value(CUSTOMER_PHONE))
                .andExpect(jsonPath("$[0].name").value("Pending barber"));
    }

    @Test
    void approvalCreatesExactlyOneLinkedProfileAndPreservesCustomerRole()
            throws Exception {
        User customer = saveVerifiedUser(CUSTOMER_PHONE);
        User admin = saveAdmin();
        submit(sessionFor(customer), "Approved barber").andExpect(status().isCreated());
        BarberApplication application = applicationRepository.findAll().getFirst();

        mockMvc.perform(post(
                        "/api/admin/barber-applications/{id}/approve",
                        application.getId()
                ).session(sessionFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());

        entityManager.flush();
        entityManager.clear();
        User approved = userRepository.findById(customer.getId()).orElseThrow();
        Barber barber = barberRepository.findAll().getFirst();
        assertEquals(Set.of(UserRole.CUSTOMER, UserRole.BARBER), approved.getRoles());
        assertEquals(customer.getId(), barber.getUser().getId());
        assertEquals(CUSTOMER_PHONE, barber.getPhone());
        assertEquals("Approved barber", barber.getName());
        assertEquals(LocalTime.of(10, 0), barber.getWorkStartTime());
        assertEquals(LocalTime.of(18, 0), barber.getWorkEndTime());
        var schedules = weeklyScheduleRepository.findByBarberId(barber.getId());
        assertEquals(7, schedules.size());
        assertEquals(6, schedules.stream().filter(schedule -> schedule.isActive()
                && schedule.getStartTime().equals(LocalTime.of(10, 0))
                && schedule.getEndTime().equals(LocalTime.of(18, 0))).count());
        var friday = schedules.stream().filter(schedule -> schedule.getDayOfWeek()
                == DayOfWeek.FRIDAY).findFirst().orElseThrow();
        assertFalse(friday.isActive());
        assertNull(friday.getStartTime());
        assertNull(friday.getEndTime());

        mockMvc.perform(post(
                        "/api/admin/barber-applications/{id}/approve",
                        application.getId()
                ).session(sessionFor(admin)))
                .andExpect(status().isConflict());
        mockMvc.perform(post(
                        "/api/admin/barber-applications/{id}/reject",
                        application.getId()
                ).session(sessionFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"too late\"}"))
                .andExpect(status().isConflict());
        assertEquals(1, barberRepository.count());
        assertEquals(7, weeklyScheduleRepository.count());
    }

    @Test
    void rejectionCreatesNoProfileOrRoleAndCannotBeRepeated() throws Exception {
        User customer = saveVerifiedUser(CUSTOMER_PHONE);
        User admin = saveAdmin();
        submit(sessionFor(customer), "Rejected barber").andExpect(status().isCreated());
        BarberApplication application = applicationRepository.findAll().getFirst();

        reject(sessionFor(admin), application.getId(), "Not approved")
                .andExpect(status().isOk());
        reject(sessionFor(admin), application.getId(), "Again")
                .andExpect(status().isConflict());
        mockMvc.perform(post(
                        "/api/admin/barber-applications/{id}/approve",
                        application.getId()
                ).session(sessionFor(admin)))
                .andExpect(status().isConflict());

        assertEquals(0, barberRepository.count());
        assertEquals(Set.of(UserRole.CUSTOMER),
                userRepository.findById(customer.getId()).orElseThrow().getRoles());
    }

    @Test
    void legacyBarberWithMatchingPhoneIsNotAutoLinked() throws Exception {
        User customer = saveVerifiedUser(CUSTOMER_PHONE);
        User admin = saveAdmin();
        Barber legacy = barberRepository.save(new Barber(
                "Legacy",
                CUSTOMER_PHONE,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0)
        ));
        submit(sessionFor(customer), "New linked profile").andExpect(status().isCreated());
        BarberApplication application = applicationRepository.findAll().getFirst();

        mockMvc.perform(post(
                        "/api/admin/barber-applications/{id}/approve",
                        application.getId()
                ).session(sessionFor(admin)))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        assertNull(barberRepository.findById(legacy.getId()).orElseThrow().getUser());
        assertEquals(2, barberRepository.count());
        assertTrue(barberRepository.existsByUserId(customer.getId()));
    }

    @Test
    void onlyAdminCanCreateBarberDirectly() throws Exception {
        User customer = saveVerifiedUser(CUSTOMER_PHONE);
        User admin = saveAdmin();
        String body = """
                {
                  "name": "Direct barber",
                  "phone": "09120000000",
                  "workStartTime": "10:00",
                  "workEndTime": "18:00"
                }
                """;

        mockMvc.perform(post("/api/barbers")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/barbers").session(sessionFor(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/barbers").session(sessionFor(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        assertEquals(1, barberRepository.count());
        assertNull(barberRepository.findAll().getFirst().getUser());
    }

    private User saveVerifiedUser(String phone) {
        User user = new User(phone);
        user.verifyPhone();
        return userRepository.save(user);
    }

    private User saveAdmin() {
        User admin = new User(ADMIN_PHONE);
        admin.verifyPhone();
        admin.grantAdminRole();
        return userRepository.save(admin);
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

    private org.springframework.test.web.servlet.ResultActions submit(
            MockHttpSession session,
            String name
    ) throws Exception {
        return mockMvc.perform(post("/api/me/barber-application")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validApplicationJson(name)));
    }

    private org.springframework.test.web.servlet.ResultActions reject(
            MockHttpSession session,
            Long applicationId,
            String note
    ) throws Exception {
        return mockMvc.perform(post(
                        "/api/admin/barber-applications/{id}/reject",
                        applicationId
                ).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"" + note + "\"}"));
    }

    private String validApplicationJson(String name) {
        return """
                {
                  "name": "%s",
                  "workStartTime": "10:00",
                  "workEndTime": "18:00"
                }
                """.formatted(name);
    }
}
