package com.example.barbershop;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BlockedTimeIntegrationTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 12);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BarberRepository barberRepository;

    @Autowired
    private BarberServiceOfferingRepository barberServiceOfferingRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private BlockedTimeRepository blockedTimeRepository;

    @Test
    void createsListsAndDeletesBlockAndReopensAvailability() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");
        Barber otherBarber = saveBarber("Sara Ahmadi");
        BarberServiceOffering service = saveService(barber, 30);
        blockedTimeRepository.save(new BlockedTime(
                barber,
                DATE.plusDays(1),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                "Other date"
        ));
        blockedTimeRepository.save(new BlockedTime(
                otherBarber,
                DATE,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                "Other barber"
        ));

        mockMvc.perform(post("/api/blocked-times")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(barber.getId(), "13:00", "14:00", "Lunch")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.barberId").value(barber.getId()))
                .andExpect(jsonPath("$.barberName").value("Ali Rezaei"))
                .andExpect(jsonPath("$.date").value("2026-09-12"))
                .andExpect(jsonPath("$.startTime").value("13:00:00"))
                .andExpect(jsonPath("$.endTime").value("14:00:00"))
                .andExpect(jsonPath("$.reason").value("Lunch"));
        Long blockId = blockedTimeRepository.findByBarberIdAndDate(
                barber.getId(), DATE
        ).getFirst().getId();

        mockMvc.perform(get("/api/blocked-times")
                        .param("barberId", barber.getId().toString())
                        .param("date", DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(blockId));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(14))
                .andExpect(jsonPath("$[5].startTime").value("12:30:00"))
                .andExpect(jsonPath("$[6].startTime").value("14:00:00"));

        mockMvc.perform(delete("/api/blocked-times/{blockedTimeId}", blockId))
                .andExpect(status().isNoContent());

        assertFalse(blockedTimeRepository.existsById(blockId));
        mockMvc.perform(get("/api/barbers/{barberId}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$[6].startTime").value("13:00:00"));
    }

    @Test
    void excludesSixtyMinuteServiceWhenAnyPartOverlapsBlock() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");
        BarberServiceOffering service = saveService(barber, 60);
        blockedTimeRepository.save(new BlockedTime(
                barber,
                DATE,
                LocalTime.of(10, 30),
                LocalTime.of(11, 0),
                null
        ));

        mockMvc.perform(get("/api/barbers/{barberId}/available-times", barber.getId())
                        .param("date", DATE.toString())
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[0].startTime").value("11:00:00"));
    }

    @Test
    void appointmentCreationInsideBlockReturnsConflict() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");
        BarberServiceOffering service = saveService(barber, 30);
        saveBlock(barber, LocalTime.of(13, 0), LocalTime.of(14, 0));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(
                                barber.getId(), service.getId(), "13:30"
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Appointment overlaps blocked time"));

        assertEquals(0, appointmentRepository.count());
    }

    @Test
    void appointmentRescheduleIntoBlockReturnsConflict() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");
        BarberServiceOffering service = saveService(barber, 30);
        Appointment appointment = appointmentRepository.save(new Appointment(
                barber,
                service,
                DATE,
                LocalTime.of(10, 0),
                "Reza Karimi"
        ));
        saveBlock(barber, LocalTime.of(13, 0), LocalTime.of(14, 0));

        mockMvc.perform(patch(
                        "/api/appointments/{appointmentId}/reschedule",
                        appointment.getId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": %d,
                                  "date": "2026-09-12",
                                  "time": "13:30"
                                }
                                """.formatted(service.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Appointment overlaps blocked time"));

        assertEquals(LocalTime.of(10, 0), appointment.getTime());
    }

    @Test
    void invalidBlockAndMissingResourcesUseStableErrors() throws Exception {
        Barber barber = saveBarber("Ali Rezaei");

        mockMvc.perform(post("/api/blocked-times")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockJson(barber.getId(), "13:15", "14:00", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Blocked time is invalid"));

        mockMvc.perform(get("/api/blocked-times")
                        .param("barberId", "999999")
                        .param("date", DATE.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Barber not found with id: 999999"));

        mockMvc.perform(delete("/api/blocked-times/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Blocked time not found with id: 999999"));
    }

    private Barber saveBarber(String name) {
        return barberRepository.save(new Barber(
                name,
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
    }

    private BarberServiceOffering saveService(Barber barber, int durationMinutes) {
        return barberServiceOfferingRepository.save(new BarberServiceOffering(
                barber, "Haircut", durationMinutes, 400000L
        ));
    }

    private void saveBlock(Barber barber, LocalTime startTime, LocalTime endTime) {
        blockedTimeRepository.save(new BlockedTime(
                barber, DATE, startTime, endTime, "Lunch"
        ));
    }

    private String blockJson(
            Long barberId,
            String startTime,
            String endTime,
            String reason
    ) {
        String reasonField = reason == null ? "null" : "\"" + reason + "\"";
        return """
                {
                  "barberId": %d,
                  "date": "2026-09-12",
                  "startTime": "%s",
                  "endTime": "%s",
                  "reason": %s
                }
                """.formatted(barberId, startTime, endTime, reasonField);
    }

    private String appointmentJson(Long barberId, Long serviceId, String time) {
        return """
                {
                  "barberId": %d,
                  "serviceId": %d,
                  "date": "2026-09-12",
                  "time": "%s",
                  "clientName": "Reza Karimi"
                }
                """.formatted(barberId, serviceId, time);
    }
}
