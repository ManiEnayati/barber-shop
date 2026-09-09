package com.example.barbershop;

import com.example.barbershop.entity.Barber;
import com.example.barbershop.repository.BarberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BarberServiceCatalogIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BarberRepository barberRepository;

    @Test
    void createsBarberServicesAndReturnsThemByBarber() throws Exception {
        mockMvc.perform(post("/api/barbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Ali Rezaei",
                                  "phone": "09120000000",
                                  "workStartTime": "10:00",
                                  "workEndTime": "18:00"
                                }
                                """))
                .andExpect(status().isCreated());

        Barber barber = barberRepository.findAll().getFirst();

        createService(barber.getId(), "Haircut", 30, 400000L);
        createService(barber.getId(), "Beard", 30, 200000L);

        mockMvc.perform(get("/api/barber-services")
                        .param("barberId", barber.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name")
                        .value(containsInAnyOrder("Haircut", "Beard")))
                .andExpect(jsonPath("$[*].barberId")
                        .value(containsInAnyOrder(
                                barber.getId().intValue(),
                                barber.getId().intValue()
                        )));
    }

    private void createService(
            Long barberId,
            String name,
            int durationMinutes,
            long price
    ) throws Exception {
        mockMvc.perform(post("/api/barber-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barberId": %d,
                                  "name": "%s",
                                  "durationMinutes": %d,
                                  "price": %d
                                }
                                """.formatted(
                                barberId,
                                name,
                                durationMinutes,
                                price
                        )))
                .andExpect(status().isCreated());
    }
}
