package com.example.barbershop.controller;

import com.example.barbershop.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
class AppointmentControllerTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AppointmentService appointmentService;

    @Test
    void legacyAppointmentCreationIsNotExposed() throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(appointmentService);
    }

    @Test
    void legacyCancelAndRescheduleEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(patch("/api/appointments/100/cancel"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/appointments/100/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(appointmentService);
    }

    @Test
    void legacyLifecycleAndRejectEndpointsAreNotExposed() throws Exception {
        for (String action : new String[]{"arrive", "complete", "no-show"}) {
            mockMvc.perform(patch("/api/appointments/100/" + action))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(post("/api/appointments/100/reject"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(appointmentService);
    }
}
