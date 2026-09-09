package com.example.barbershop.controller;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.BarberServiceDoesNotBelongToBarberException;
import com.example.barbershop.exception.BarberServiceOfferingNotFoundException;
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
    void createsServiceAwareAppointment() throws Exception {
        AppointmentCreateRequest request = request(1L, 10L, LocalTime.of(14, 30));
        when(appointmentService.create(request)).thenReturn(new AppointmentResponse(
                100L,
                1L,
                "Ali Rezaei",
                10L,
                "Haircut",
                30,
                request.date(),
                request.time(),
                LocalTime.of(15, 0),
                request.clientName()
        ));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAppointmentJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.barberId").value(1))
                .andExpect(jsonPath("$.barberName").value("Ali Rezaei"))
                .andExpect(jsonPath("$.serviceId").value(10))
                .andExpect(jsonPath("$.serviceName").value("Haircut"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.date").value("2026-09-10"))
                .andExpect(jsonPath("$.time").value("14:30:00"))
                .andExpect(jsonPath("$.endTime").value("15:00:00"))
                .andExpect(jsonPath("$.clientName").value("Reza Karimi"));

        verify(appointmentService).create(request);
    }

    @Test
    void rejectsBlankClientNameWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "date": "2026-09-10",
                  "time": "14:30",
                  "clientName": " "
                }
                """);
    }

    @Test
    void rejectsMissingBarberIdWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "serviceId": 10,
                  "date": "2026-09-10",
                  "time": "14:30",
                  "clientName": "Reza Karimi"
                }
                """);
    }

    @Test
    void rejectsMissingServiceIdWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "date": "2026-09-10",
                  "time": "14:30",
                  "clientName": "Reza Karimi"
                }
                """);
    }

    @Test
    void rejectsMissingDateWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "time": "14:30",
                  "clientName": "Reza Karimi"
                }
                """);
    }

    @Test
    void rejectsMissingTimeWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "date": "2026-09-10",
                  "clientName": "Reza Karimi"
                }
                """);
    }

    @Test
    void rejectsInvalidDateJsonWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "date": "2026-99-10",
                  "time": "14:30",
                  "clientName": "Reza Karimi"
                }
                """);
    }

    @Test
    void rejectsInvalidTimeJsonWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "date": "2026-09-10",
                  "time": "25:30",
                  "clientName": "Reza Karimi"
                }
                """);
    }

    @Test
    void returnsConflictWhenAppointmentOverlapsExistingAppointment() throws Exception {
        AppointmentCreateRequest request = request(1L, 10L, LocalTime.of(14, 30));
        when(appointmentService.create(request))
                .thenThrow(new AppointmentSlotAlreadyBookedException());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAppointmentJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Appointment slot is already booked"));
    }

    @Test
    void returnsNotFoundWhenBarberDoesNotExist() throws Exception {
        AppointmentCreateRequest request = request(1L, 10L, LocalTime.of(14, 30));
        when(appointmentService.create(request))
                .thenThrow(new BarberNotFoundException(1L));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAppointmentJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Barber not found with id: 1"));
    }

    @Test
    void returnsNotFoundWhenServiceDoesNotExist() throws Exception {
        AppointmentCreateRequest request = request(1L, 10L, LocalTime.of(14, 30));
        when(appointmentService.create(request))
                .thenThrow(new BarberServiceOfferingNotFoundException(10L));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAppointmentJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Barber service not found with id: 10"));
    }

    @Test
    void returnsBadRequestWhenServiceBelongsToAnotherBarber() throws Exception {
        AppointmentCreateRequest request = request(1L, 10L, LocalTime.of(14, 30));
        when(appointmentService.create(request))
                .thenThrow(new BarberServiceDoesNotBelongToBarberException());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAppointmentJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Barber service does not belong to the selected barber"
                ));
    }

    @Test
    void returnsBadRequestWhenServiceDoesNotFitBarberSchedule() throws Exception {
        AppointmentCreateRequest request = request(1L, 10L, LocalTime.of(14, 30));
        when(appointmentService.create(request))
                .thenThrow(new InvalidAppointmentTimeException());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAppointmentJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Appointment time is outside the allowed schedule"));
    }

    private AppointmentCreateRequest request(
            Long barberId,
            Long serviceId,
            LocalTime time
    ) {
        return new AppointmentCreateRequest(
                barberId,
                serviceId,
                LocalDate.of(2026, 9, 10),
                time,
                "Reza Karimi"
        );
    }

    private void assertBadRequestWithoutServiceCall(String content) throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(appointmentService);
    }

    private String validAppointmentJson() {
        return """
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "date": "2026-09-10",
                  "time": "14:30",
                  "clientName": "Reza Karimi"
                }
                """;
    }
}
