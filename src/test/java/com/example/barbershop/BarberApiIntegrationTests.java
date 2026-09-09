package com.example.barbershop;

import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
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

    @Autowired
    private BarberServiceOfferingRepository barberServiceOfferingRepository;

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
        BarberServiceOffering firstService = barberServiceOfferingRepository.save(
                new BarberServiceOffering(firstBarber, "Haircut", 30, 400000L)
        );
        BarberServiceOffering secondService = barberServiceOfferingRepository.save(
                new BarberServiceOffering(secondBarber, "Beard", 30, 200000L)
        );

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", firstBarber.getId())
                        .param("date", "2026-09-10")
                        .param("serviceId", firstService.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[3].endTime").value("12:00:00"));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", secondBarber.getId())
                        .param("date", "2026-09-10")
                        .param("serviceId", secondService.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("09:30:00"))
                .andExpect(jsonPath("$[2].endTime").value("11:00:00"));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barberId": %d,
                                  "serviceId": %d,
                                  "date": "2026-09-10",
                                  "time": "10:30",
                                  "clientName": "Reza Karimi"
                                }
                                """.formatted(
                                firstBarber.getId(),
                                firstService.getId()
                        )))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", firstBarber.getId())
                        .param("date", "2026-09-10")
                        .param("serviceId", firstService.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[1].startTime").value("11:00:00"));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", secondBarber.getId())
                        .param("date", "2026-09-10")
                        .param("serviceId", secondService.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("09:30:00"))
                .andExpect(jsonPath("$[1].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[2].startTime").value("10:30:00"));
    }

    @Test
    void usesServiceDurationForBookingOverlapAndAvailability() throws Exception {
        Barber barber = barberRepository.save(new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(12, 0)
        ));
        BarberServiceOffering haircut = barberServiceOfferingRepository.save(
                new BarberServiceOffering(barber, "Haircut", 30, 400000L)
        );
        BarberServiceOffering hairAndBeard = barberServiceOfferingRepository.save(
                new BarberServiceOffering(barber, "Hair + Beard", 60, 550000L)
        );

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", barber.getId())
                        .param("date", "2026-09-10")
                        .param("serviceId", hairAndBeard.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("11:00:00"))
                .andExpect(jsonPath("$[2].startTime").value("11:00:00"))
                .andExpect(jsonPath("$[2].endTime").value("12:00:00"));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(
                                barber.getId(),
                                hairAndBeard.getId(),
                                "10:00",
                                "Reza Karimi"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceId").value(hairAndBeard.getId()))
                .andExpect(jsonPath("$.serviceName").value("Hair + Beard"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.time").value("10:00:00"))
                .andExpect(jsonPath("$.endTime").value("11:00:00"));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(
                                barber.getId(),
                                haircut.getId(),
                                "10:30",
                                "Mina Jafari"
                        )))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(
                                barber.getId(),
                                haircut.getId(),
                                "11:00",
                                "Mina Jafari"
                        )))
                .andExpect(status().isCreated());
    }

    private String appointmentJson(
            Long barberId,
            Long serviceId,
            String time,
            String clientName
    ) {
        return """
                {
                  "barberId": %d,
                  "serviceId": %d,
                  "date": "2026-09-10",
                  "time": "%s",
                  "clientName": "%s"
                }
                """.formatted(barberId, serviceId, time, clientName);
    }
}
