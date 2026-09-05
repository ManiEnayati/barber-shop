package com.example.barbershop.controller;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.BarberCreateRequest;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.service.AppointmentService;
import com.example.barbershop.service.BarberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BarberController.class)
class BarberControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BarberService barberService;

    @MockitoBean
    private AppointmentService appointmentService;

    @Test
    void createsBarber() throws Exception {
        when(barberService.create(any(BarberCreateRequest.class)))
                .thenReturn(new BarberResponse(1L, "Ali Rezaei", "09120000000"));

        mockMvc.perform(post("/api/barbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Ali Rezaei",
                                  "phone": "09120000000"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Ali Rezaei"))
                .andExpect(jsonPath("$.phone").value("09120000000"));
    }

    @Test
    void returnsAllBarbers() throws Exception {
        when(barberService.findAll()).thenReturn(List.of(
                new BarberResponse(1L, "Ali Rezaei", "09120000000"),
                new BarberResponse(2L, "Sara Ahmadi", "09121111111")
        ));

        mockMvc.perform(get("/api/barbers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Ali Rezaei"))
                .andExpect(jsonPath("$[1].name").value("Sara Ahmadi"));
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/barbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "phone": "09120000000"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(barberService);
    }

    @Test
    void rejectsBlankPhone() throws Exception {
        mockMvc.perform(post("/api/barbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Ali Rezaei",
                                  "phone": " "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(barberService);
    }

    @Test
    void returnsAppointmentsForBarberAndDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(appointmentService.findByBarberAndDate(1L, date)).thenReturn(List.of(
                new AppointmentResponse(
                        10L,
                        1L,
                        "Ali Rezaei",
                        date,
                        LocalTime.of(10, 30),
                        "Reza Karimi"
                ),
                new AppointmentResponse(
                        11L,
                        1L,
                        "Ali Rezaei",
                        date,
                        LocalTime.of(14, 0),
                        "Mina Jafari"
                )
        ));

        mockMvc.perform(get("/api/barbers/1/appointments")
                        .param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].barberId").value(1))
                .andExpect(jsonPath("$[0].barberName").value("Ali Rezaei"))
                .andExpect(jsonPath("$[0].date").value("2026-09-10"))
                .andExpect(jsonPath("$[0].time").value("10:30:00"))
                .andExpect(jsonPath("$[0].clientName").value("Reza Karimi"))
                .andExpect(jsonPath("$[1].id").value(11))
                .andExpect(jsonPath("$[1].time").value("14:00:00"))
                .andExpect(jsonPath("$[1].clientName").value("Mina Jafari"));

        verify(appointmentService).findByBarberAndDate(1L, date);
    }

    @Test
    void returnsEmptyListWhenBarberHasNoAppointmentsOnDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(appointmentService.findByBarberAndDate(1L, date)).thenReturn(List.of());

        mockMvc.perform(get("/api/barbers/1/appointments")
                        .param("date", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(appointmentService).findByBarberAndDate(1L, date);
    }

    @Test
    void returnsNotFoundWhenBarberDoesNotExist() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(appointmentService.findByBarberAndDate(999L, date))
                .thenThrow(new BarberNotFoundException(999L));

        mockMvc.perform(get("/api/barbers/999/appointments")
                        .param("date", "2026-09-10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Barber not found with id: 999"));

        verify(appointmentService).findByBarberAndDate(999L, date);
    }

    @Test
    void rejectsInvalidAppointmentDateWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/barbers/1/appointments")
                        .param("date", "not-a-date"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(appointmentService);
    }
}
