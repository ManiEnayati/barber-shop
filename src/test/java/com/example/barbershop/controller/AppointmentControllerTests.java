package com.example.barbershop.controller;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.InvalidAppointmentTimeException;
import com.example.barbershop.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
class AppointmentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppointmentService appointmentService;

    @Test
    void createsAppointment() throws Exception {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                1L,
                LocalDate.of(2026, 9, 10),
                LocalTime.of(14, 30),
                "Reza Karimi"
        );
        when(appointmentService.create(request)).thenReturn(new AppointmentResponse(
                10L,
                1L,
                "Ali Rezaei",
                request.date(),
                request.time(),
                request.clientName()
        ));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barberId": 1,
                                  "date": "2026-09-10",
                                  "time": "14:30",
                                  "clientName": "Reza Karimi"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.barberId").value(1))
                .andExpect(jsonPath("$.barberName").value("Ali Rezaei"))
                .andExpect(jsonPath("$.date").value("2026-09-10"))
                .andExpect(jsonPath("$.time").value("14:30:00"))
                .andExpect(jsonPath("$.clientName").value("Reza Karimi"));

        verify(appointmentService).create(request);
    }

    @Test
    void rejectsBlankClientName() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "date": "2026-09-10",
                  "time": "14:30",
                  "clientName": " "
                }
                """);
    }

    @Test
    void rejectsMissingBarberId() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "date": "2026-09-10",
                  "time": "14:30",
                  "clientName": "Reza Karimi"
                }
                """);
    }

    @Test
    void rejectsMissingDate() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "time": "14:30",
                  "clientName": "Reza Karimi"
                }
                """);
    }

    @Test
    void rejectsMissingTime() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "date": "2026-09-10",
                  "clientName": "Reza Karimi"
                }
                """);
    }

    @Test
    void rejectsInvalidDateJson() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "date": "2026-99-10",
                  "time": "14:30",
                  "clientName": "Reza Karimi"
                }
                """);
    }

    @Test
    void rejectsInvalidTimeJson() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "date": "2026-09-10",
                  "time": "25:30",
                  "clientName": "Reza Karimi"
                }
                """);
    }

    @Test
    void returnsConflictWhenAppointmentSlotIsAlreadyBooked() throws Exception {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                1L,
                LocalDate.of(2026, 9, 10),
                LocalTime.of(14, 30),
                "Reza Karimi"
        );
        when(appointmentService.create(request))
                .thenThrow(new AppointmentSlotAlreadyBookedException());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barberId": 1,
                                  "date": "2026-09-10",
                                  "time": "14:30",
                                  "clientName": "Reza Karimi"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Appointment slot is already booked"));

        verify(appointmentService).create(request);
    }

    @Test
    void returnsNotFoundWhenBarberDoesNotExist() throws Exception {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                999L,
                LocalDate.of(2026, 9, 10),
                LocalTime.of(14, 30),
                "Reza Karimi"
        );
        when(appointmentService.create(request))
                .thenThrow(new BarberNotFoundException(999L));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barberId": 999,
                                  "date": "2026-09-10",
                                  "time": "14:30",
                                  "clientName": "Reza Karimi"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Barber not found with id: 999"));

        verify(appointmentService).create(request);
    }

    @Test
    void returnsBadRequestWhenAppointmentTimeIsOutsideBarberSchedule() throws Exception {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                1L,
                LocalDate.of(2026, 9, 10),
                LocalTime.of(9, 30),
                "Reza Karimi"
        );
        when(appointmentService.create(request))
                .thenThrow(new InvalidAppointmentTimeException());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barberId": 1,
                                  "date": "2026-09-10",
                                  "time": "09:30",
                                  "clientName": "Reza Karimi"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Appointment time is outside the allowed schedule"));

        verify(appointmentService).create(request);
    }

    private void assertBadRequestWithoutServiceCall(String content) throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(appointmentService);
    }
}
