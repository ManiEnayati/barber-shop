package com.example.barbershop.controller;

import com.example.barbershop.dto.AvailableTimeResponse;
import com.example.barbershop.dto.BarberCreateRequest;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.BarberServiceDoesNotBelongToBarberException;
import com.example.barbershop.exception.BarberServiceOfferingNotFoundException;
import com.example.barbershop.exception.InvalidBarberScheduleException;
import com.example.barbershop.service.AppointmentService;
import com.example.barbershop.service.BarberService;
import com.example.barbershop.service.DailyCalendarService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
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
@WithMockUser(roles = "ADMIN")
class BarberControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BarberService barberService;

    @MockitoBean
    private AppointmentService appointmentService;

    @MockitoBean
    private DailyCalendarService dailyCalendarService;

    @Test
    void publicDetailedCalendarIsUnavailable() throws Exception {
        mockMvc.perform(get("/api/barbers/1/daily-calendar")
                        .param("date", "2026-09-13"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(dailyCalendarService);
    }

    @Test
    void createsBarberWithWorkingHours() throws Exception {
        BarberCreateRequest request = new BarberCreateRequest(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        );
        when(barberService.create(request)).thenReturn(new BarberResponse(
                1L,
                request.name(),
                request.phone(),
                request.workStartTime(),
                request.workEndTime()
        ));

        mockMvc.perform(post("/api/barbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBarberJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Ali Rezaei"))
                .andExpect(jsonPath("$.phone").value("09120000000"))
                .andExpect(jsonPath("$.workStartTime").value("10:00:00"))
                .andExpect(jsonPath("$.workEndTime").value("18:00:00"));

        verify(barberService).create(request);
    }

    @Test
    void returnsAllBarbersWithWorkingHours() throws Exception {
        when(barberService.findAll()).thenReturn(List.of(
                new BarberResponse(
                        1L,
                        "Ali Rezaei",
                        "09120000000",
                        LocalTime.of(10, 0),
                        LocalTime.of(18, 0)
                ),
                new BarberResponse(
                        2L,
                        "Sara Ahmadi",
                        "09121111111",
                        LocalTime.of(9, 30),
                        LocalTime.of(17, 0)
                )
        ));

        mockMvc.perform(get("/api/barbers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Ali Rezaei"))
                .andExpect(jsonPath("$[0].workStartTime").value("10:00:00"))
                .andExpect(jsonPath("$[0].workEndTime").value("18:00:00"))
                .andExpect(jsonPath("$[1].name").value("Sara Ahmadi"))
                .andExpect(jsonPath("$[1].workStartTime").value("09:30:00"))
                .andExpect(jsonPath("$[1].workEndTime").value("17:00:00"));
    }

    @Test
    void rejectsBlankName() throws Exception {
        assertInvalidBarberRequestWithoutServiceCall("""
                {
                  "name": " ",
                  "phone": "09120000000",
                  "workStartTime": "10:00",
                  "workEndTime": "18:00"
                }
                """);
    }

    @Test
    void rejectsBlankPhone() throws Exception {
        assertInvalidBarberRequestWithoutServiceCall("""
                {
                  "name": "Ali Rezaei",
                  "phone": " ",
                  "workStartTime": "10:00",
                  "workEndTime": "18:00"
                }
                """);
    }

    @Test
    void rejectsMissingWorkStartTimeWithoutCallingService() throws Exception {
        assertInvalidBarberRequestWithoutServiceCall("""
                {
                  "name": "Ali Rezaei",
                  "phone": "09120000000",
                  "workEndTime": "18:00"
                }
                """);
    }

    @Test
    void rejectsMissingWorkEndTimeWithoutCallingService() throws Exception {
        assertInvalidBarberRequestWithoutServiceCall("""
                {
                  "name": "Ali Rezaei",
                  "phone": "09120000000",
                  "workStartTime": "10:00"
                }
                """);
    }

    @Test
    void returnsBadRequestForInvalidBarberSchedule() throws Exception {
        when(barberService.create(any(BarberCreateRequest.class)))
                .thenThrow(new InvalidBarberScheduleException());

        mockMvc.perform(post("/api/barbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBarberJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Barber work schedule is invalid"));

        verify(barberService).create(any(BarberCreateRequest.class));
    }

    @Test
    void publicDetailedAppointmentListIsUnavailable() throws Exception {
        mockMvc.perform(get("/api/barbers/1/appointments")
                        .param("date", "2026-09-10"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void publicDetailedAppointmentListRemainsUnavailableForAnyDate()
            throws Exception {
        mockMvc.perform(get("/api/barbers/1/appointments")
                        .param("date", "2026-09-10"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void publicDetailedAppointmentListDoesNotRevealBarberExistence()
            throws Exception {
        mockMvc.perform(get("/api/barbers/999/appointments")
                        .param("date", "2026-09-10"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void publicDetailedAppointmentListIsUnavailableWithInvalidDate()
            throws Exception {
        mockMvc.perform(get("/api/barbers/1/appointments")
                        .param("date", "not-a-date"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void returnsAvailableTimeRangesForBarberAndDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(appointmentService.findAvailableTimes(1L, date, 100L)).thenReturn(List.of(
                new AvailableTimeResponse(LocalTime.of(10, 0), LocalTime.of(11, 0)),
                new AvailableTimeResponse(LocalTime.of(10, 30), LocalTime.of(11, 30))
        ));

        mockMvc.perform(get("/api/barbers/1/available-times")
                        .param("date", "2026-09-10")
                        .param("serviceId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[0].endTime").value("11:00:00"))
                .andExpect(jsonPath("$[1].startTime").value("10:30:00"))
                .andExpect(jsonPath("$[1].endTime").value("11:30:00"));

        verify(appointmentService).findAvailableTimes(1L, date, 100L);
    }

    @Test
    void returnsEmptyListWhenNoTimesAreAvailable() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(appointmentService.findAvailableTimes(1L, date, 100L)).thenReturn(List.of());

        mockMvc.perform(get("/api/barbers/1/available-times")
                        .param("date", "2026-09-10")
                        .param("serviceId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(appointmentService).findAvailableTimes(1L, date, 100L);
    }

    @Test
    void returnsNotFoundWhenFindingAvailableTimesForMissingBarber() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(appointmentService.findAvailableTimes(999L, date, 100L))
                .thenThrow(new BarberNotFoundException(999L));

        mockMvc.perform(get("/api/barbers/999/available-times")
                        .param("date", "2026-09-10")
                        .param("serviceId", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Barber not found with id: 999"));

        verify(appointmentService).findAvailableTimes(999L, date, 100L);
    }

    @Test
    void returnsNotFoundWhenFindingAvailableTimesForMissingService() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(appointmentService.findAvailableTimes(1L, date, 999L))
                .thenThrow(new BarberServiceOfferingNotFoundException(999L));

        mockMvc.perform(get("/api/barbers/1/available-times")
                        .param("date", "2026-09-10")
                        .param("serviceId", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Barber service not found with id: 999"));

        verify(appointmentService).findAvailableTimes(1L, date, 999L);
    }

    @Test
    void rejectsAvailabilityForServiceOwnedByAnotherBarber() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 10);
        when(appointmentService.findAvailableTimes(1L, date, 100L))
                .thenThrow(new BarberServiceDoesNotBelongToBarberException());

        mockMvc.perform(get("/api/barbers/1/available-times")
                        .param("date", "2026-09-10")
                        .param("serviceId", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Barber service does not belong to the selected barber"
                ));

        verify(appointmentService).findAvailableTimes(1L, date, 100L);
    }

    @Test
    void rejectsMissingAvailableTimesServiceIdWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/barbers/1/available-times")
                        .param("date", "2026-09-10"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void rejectsInvalidAvailableTimesDateWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/barbers/1/available-times")
                        .param("date", "not-a-date")
                        .param("serviceId", "100"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(appointmentService);
    }

    private void assertInvalidBarberRequestWithoutServiceCall(String content) throws Exception {
        mockMvc.perform(post("/api/barbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(barberService);
    }

    private String validBarberJson() {
        return """
                {
                  "name": "Ali Rezaei",
                  "phone": "09120000000",
                  "workStartTime": "10:00",
                  "workEndTime": "18:00"
                }
                """;
    }
}
