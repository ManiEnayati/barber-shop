package com.example.barbershop;

import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.User;
import com.example.barbershop.repository.BarberRepository;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BarberServiceCatalogIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;

    @Test
    void owningBarberCreatesServicesAndPublicCatalogReturnsThem() throws Exception {
        User user = new User("+989120003001");
        user.verifyPhone();
        user.approveBarber();
        userRepository.save(user);
        Barber barber = barberRepository.save(new Barber(
                user, "Ali Rezaei", LocalTime.of(10, 0), LocalTime.of(18, 0)));
        MockHttpSession session = sessionFor(user);

        createService(session, "Haircut", 30, 400000L);
        createService(session, "Beard", 30, 200000L);

        mockMvc.perform(get("/api/barber-services")
                        .param("barberId", barber.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name")
                        .value(containsInAnyOrder("Haircut", "Beard")));
    }

    private void createService(
            MockHttpSession session,
            String name,
            int durationMinutes,
            long price
    ) throws Exception {
        mockMvc.perform(post("/api/me/barber/services").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name
                                + "\",\"durationMinutes\":" + durationMinutes
                                + ",\"price\":" + price + "}"))
                .andExpect(status().isCreated());
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
