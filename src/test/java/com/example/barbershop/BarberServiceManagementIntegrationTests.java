package com.example.barbershop;

import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.User;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
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

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BarberServiceManagementIntegrationTests {

    private static final String SECURE_PATH = "/api/me/barber/services";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository offeringRepository;

    @Test
    void owningBarberCanCreateUpdateAndDeleteWhilePublicGetRemainsAvailable()
            throws Exception {
        Barber barber = barber("+989120002001");
        MockHttpSession session = sessionFor(barber.getUser());

        mockMvc.perform(post(SECURE_PATH).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceJson("Haircut", 30, 400000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.barberId").value(barber.getId()))
                .andExpect(jsonPath("$.name").value("Haircut"));
        Long serviceId = offeringRepository.findAll().getFirst().getId();

        mockMvc.perform(put(SECURE_PATH + "/{id}", serviceId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceJson("Hair and beard", 60, 700000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(60));
        mockMvc.perform(get("/api/barber-services")
                        .param("barberId", barber.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Hair and beard"));

        mockMvc.perform(delete(SECURE_PATH + "/{id}", serviceId).session(session))
                .andExpect(status().isNoContent());
        assertFalse(offeringRepository.existsById(serviceId));
    }

    @Test
    void anonymousAndNonOwnerCannotWriteServices() throws Exception {
        Barber owner = barber("+989120002002");
        Barber other = barber("+989120002003");
        BarberServiceOffering offering = offeringRepository.save(
                new BarberServiceOffering(owner, "Haircut", 30, 400000));

        mockMvc.perform(post(SECURE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceJson("Beard", 30, 200000)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(SECURE_PATH + "/{id}", offering.getId())
                        .session(sessionFor(other.getUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceJson("Changed", 30, 200000)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(SECURE_PATH + "/{id}", offering.getId())
                        .session(sessionFor(other.getUser())))
                .andExpect(status().isForbidden());

        assertEquals("Haircut", offeringRepository.findById(
                offering.getId()).orElseThrow().getName());
    }

    @Test
    void legacyPublicServiceWriteIsRemovedForAnonymousAndAuthenticatedCallers()
            throws Exception {
        Barber barber = barber("+989120002004");
        BarberServiceOffering existing = offeringRepository.save(
                new BarberServiceOffering(barber, "Existing", 30, 300000));
        String body = "{\"barberId\":" + barber.getId()
                + ",\"name\":\"Haircut\",\"durationMinutes\":30,\"price\":400000}";

        mockMvc.perform(post("/api/barber-services")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/barber-services")
                        .session(sessionFor(barber.getUser()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/barber-services/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/barber-services/{id}", existing.getId())
                        .session(sessionFor(barber.getUser())))
                .andExpect(status().isForbidden());

        assertEquals(1, offeringRepository.count());
        assertEquals("Existing", offeringRepository.findById(
                existing.getId()).orElseThrow().getName());
    }

    private Barber barber(String phone) {
        User user = new User(phone);
        user.verifyPhone();
        user.approveBarber();
        userRepository.save(user);
        return barberRepository.save(new Barber(
                user, "Barber", LocalTime.of(8, 0), LocalTime.of(20, 0)));
    }

    private String serviceJson(String name, int duration, long price) {
        return "{\"name\":\"" + name + "\",\"durationMinutes\":" + duration
                + ",\"price\":" + price + "}";
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
}
