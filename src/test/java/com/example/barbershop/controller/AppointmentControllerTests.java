package com.example.barbershop.controller;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.exception.AppointmentCannotBeCompletedException;
import com.example.barbershop.exception.AppointmentCannotBeMarkedArrivedException;
import com.example.barbershop.exception.AppointmentCannotBeMarkedNoShowException;
import com.example.barbershop.exception.AppointmentNotFoundException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
                100L,
                "Reza Karimi",
                "09123334444",
                AppointmentStatus.BOOKED
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
                .andExpect(jsonPath("$.customerId").value(100))
                .andExpect(jsonPath("$.customerName").value("Reza Karimi"))
                .andExpect(jsonPath("$.customerPhone").value("09123334444"))
                .andExpect(jsonPath("$.status").value("BOOKED"));

        verify(appointmentService).create(request);
    }

    @Test
    void rejectsMissingCustomerIdWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "date": "2026-09-10",
                  "time": "14:30"
                }
                """);
    }

    @Test
    void rejectsMissingBarberIdWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "serviceId": 10,
                  "customerId": 100,
                  "date": "2026-09-10",
                  "time": "14:30"
                }
                """);
    }

    @Test
    void rejectsMissingServiceIdWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "customerId": 100,
                  "date": "2026-09-10",
                  "time": "14:30"
                }
                """);
    }

    @Test
    void rejectsMissingDateWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "customerId": 100,
                  "time": "14:30"
                }
                """);
    }

    @Test
    void rejectsMissingTimeWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "customerId": 100,
                  "date": "2026-09-10"
                }
                """);
    }

    @Test
    void rejectsInvalidDateJsonWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "customerId": 100,
                  "date": "2026-99-10",
                  "time": "14:30"
                }
                """);
    }

    @Test
    void rejectsInvalidTimeJsonWithoutCallingService() throws Exception {
        assertBadRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "serviceId": 10,
                  "customerId": 100,
                  "date": "2026-09-10",
                  "time": "25:30"
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

    @Test
    void legacyCancelEndpointIsNotExposed() throws Exception {
        mockMvc.perform(patch("/api/appointments/100/cancel"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void marksAppointmentArrived() throws Exception {
        when(appointmentService.markArrived(100L))
                .thenReturn(response(AppointmentStatus.ARRIVED));

        mockMvc.perform(patch("/api/appointments/100/arrive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"));

        verify(appointmentService).markArrived(100L);
    }

    @Test
    void completesAppointment() throws Exception {
        when(appointmentService.complete(100L))
                .thenReturn(response(AppointmentStatus.COMPLETED));

        mockMvc.perform(patch("/api/appointments/100/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(appointmentService).complete(100L);
    }

    @Test
    void marksAppointmentNoShow() throws Exception {
        when(appointmentService.markNoShow(100L))
                .thenReturn(response(AppointmentStatus.NO_SHOW));

        mockMvc.perform(patch("/api/appointments/100/no-show"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));

        verify(appointmentService).markNoShow(100L);
    }

    @Test
    void returnsNotFoundWhenMarkingMissingAppointmentArrived() throws Exception {
        when(appointmentService.markArrived(999L))
                .thenThrow(new AppointmentNotFoundException(999L));

        mockMvc.perform(patch("/api/appointments/999/arrive"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Appointment not found with id: 999"));
    }

    @Test
    void returnsBadRequestWhenAppointmentCannotBeMarkedArrived() throws Exception {
        when(appointmentService.markArrived(100L))
                .thenThrow(new AppointmentCannotBeMarkedArrivedException());

        mockMvc.perform(patch("/api/appointments/100/arrive"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Appointment cannot be marked as arrived"));
    }

    @Test
    void returnsBadRequestWhenAppointmentCannotBeCompleted() throws Exception {
        when(appointmentService.complete(100L))
                .thenThrow(new AppointmentCannotBeCompletedException());

        mockMvc.perform(patch("/api/appointments/100/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Appointment cannot be completed"));
    }

    @Test
    void returnsBadRequestWhenAppointmentCannotBeMarkedNoShow() throws Exception {
        when(appointmentService.markNoShow(100L))
                .thenThrow(new AppointmentCannotBeMarkedNoShowException());

        mockMvc.perform(patch("/api/appointments/100/no-show"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Appointment cannot be marked as no-show"));
    }

    @Test
    void legacyRescheduleEndpointIsNotExposed() throws Exception {
        mockMvc.perform(patch("/api/appointments/100/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": 20,
                                  "date": "2026-09-11",
                                  "time": "11:00"
                                }
                                """))
                .andExpect(status().isNotFound());

        verifyNoInteractions(appointmentService);
    }

    private AppointmentCreateRequest request(
            Long barberId,
            Long serviceId,
            LocalTime time
    ) {
        return new AppointmentCreateRequest(
                barberId,
                serviceId,
                100L,
                LocalDate.of(2026, 9, 10),
                time
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
                  "customerId": 100,
                  "date": "2026-09-10",
                  "time": "14:30"
                }
                """;
    }

    private AppointmentResponse response(AppointmentStatus status) {
        return new AppointmentResponse(
                100L,
                1L,
                "Ali Rezaei",
                10L,
                "Haircut",
                30,
                LocalDate.of(2026, 9, 10),
                LocalTime.of(14, 30),
                LocalTime.of(15, 0),
                100L,
                "Reza Karimi",
                "09123334444",
                status
        );
    }
}
