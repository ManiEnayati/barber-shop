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

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BarberApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BarberRepository barberRepository;

    @Test
    void createsAndReturnsPersistedBarber() throws Exception {
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Ali Rezaei"))
                .andExpect(jsonPath("$.phone").value("09120000000"))
                .andExpect(jsonPath("$.workStartTime").value("10:00:00"))
                .andExpect(jsonPath("$.workEndTime").value("18:00:00"));

        assertEquals(1, barberRepository.count());

        mockMvc.perform(get("/api/barbers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Ali Rezaei"))
                .andExpect(jsonPath("$[0].phone").value("09120000000"))
                .andExpect(jsonPath("$[0].workStartTime").value("10:00:00"))
                .andExpect(jsonPath("$[0].workEndTime").value("18:00:00"));
    }

    @Test
    void usesIndependentSchedulesAndBookingsForDifferentBarbers() throws Exception {
        Barber firstBarber = barberRepository.save(new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(12, 0)
        ));
        Barber secondBarber = barberRepository.save(new Barber(
                "Sara Ahmadi",
                "09121111111",
                LocalTime.of(9, 30),
                LocalTime.of(11, 0)
        ));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", firstBarber.getId())
                        .param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[3].endTime").value("12:00:00"));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", secondBarber.getId())
                        .param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("09:30:00"))
                .andExpect(jsonPath("$[2].endTime").value("11:00:00"));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barberId": %d,
                                  "date": "2026-09-10",
                                  "time": "10:30",
                                  "clientName": "Reza Karimi"
                                }
                                """.formatted(firstBarber.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", firstBarber.getId())
                        .param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[1].startTime").value("11:00:00"));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", secondBarber.getId())
                        .param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("09:30:00"))
                .andExpect(jsonPath("$[1].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[2].startTime").value("10:30:00"));
    }
}
