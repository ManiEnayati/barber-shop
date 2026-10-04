package com.example.barbershop.controller;

import com.example.barbershop.dto.BarberServiceResponse;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.service.BarberServiceOfferingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BarberServiceOfferingController.class)
class BarberServiceOfferingControllerTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BarberServiceOfferingService service;

    @Test
    void legacyPublicCreateIsNotExposed() throws Exception {
        mockMvc.perform(post("/api/barber-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    @Test
    void returnsServicesForBarber() throws Exception {
        when(service.findByBarber(1L)).thenReturn(List.of(
                new BarberServiceResponse(
                        10L, 1L, "Ali Rezaei", "Haircut", 30, 400000L),
                new BarberServiceResponse(
                        11L, 1L, "Ali Rezaei", "Beard", 30, 200000L)));

        mockMvc.perform(get("/api/barber-services").param("barberId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Haircut"))
                .andExpect(jsonPath("$[1].name").value("Beard"));
        verify(service).findByBarber(1L);
    }

    @Test
    void returnsEmptyListWhenBarberHasNoServices() throws Exception {
        when(service.findByBarber(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/barber-services").param("barberId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verify(service).findByBarber(1L);
    }

    @Test
    void returnsNotFoundWhenListingServicesForMissingBarber() throws Exception {
        when(service.findByBarber(999L))
                .thenThrow(new BarberNotFoundException(999L));
        mockMvc.perform(get("/api/barber-services").param("barberId", "999"))
                .andExpect(status().isNotFound());
        verify(service).findByBarber(999L);
    }
}
