package com.example.barbershop.controller;

import com.example.barbershop.dto.BarberServiceCreateRequest;
import com.example.barbershop.dto.BarberServiceResponse;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.InvalidBarberServiceException;
import com.example.barbershop.service.BarberServiceOfferingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BarberServiceOfferingController.class)
class BarberServiceOfferingControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BarberServiceOfferingService barberServiceOfferingService;

    @Test
    void createsBarberService() throws Exception {
        BarberServiceCreateRequest request =
                new BarberServiceCreateRequest(1L, "Haircut", 30, 400000L);
        when(barberServiceOfferingService.create(request)).thenReturn(
                new BarberServiceResponse(
                        10L,
                        1L,
                        "Ali Rezaei",
                        "Haircut",
                        30,
                        400000L
                )
        );

        mockMvc.perform(post("/api/barber-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validServiceJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.barberId").value(1))
                .andExpect(jsonPath("$.barberName").value("Ali Rezaei"))
                .andExpect(jsonPath("$.name").value("Haircut"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.price").value(400000));

        verify(barberServiceOfferingService).create(request);
    }

    @Test
    void rejectsBlankNameWithoutCallingService() throws Exception {
        assertInvalidRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "name": " ",
                  "durationMinutes": 30,
                  "price": 400000
                }
                """);
    }

    @Test
    void rejectsMissingBarberIdWithoutCallingService() throws Exception {
        assertInvalidRequestWithoutServiceCall("""
                {
                  "name": "Haircut",
                  "durationMinutes": 30,
                  "price": 400000
                }
                """);
    }

    @Test
    void rejectsMissingDurationWithoutCallingService() throws Exception {
        assertInvalidRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "name": "Haircut",
                  "price": 400000
                }
                """);
    }

    @Test
    void rejectsMissingPriceWithoutCallingService() throws Exception {
        assertInvalidRequestWithoutServiceCall("""
                {
                  "barberId": 1,
                  "name": "Haircut",
                  "durationMinutes": 30
                }
                """);
    }

    @Test
    void returnsBadRequestForInvalidBarberService() throws Exception {
        when(barberServiceOfferingService.create(any(BarberServiceCreateRequest.class)))
                .thenThrow(new InvalidBarberServiceException());

        mockMvc.perform(post("/api/barber-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validServiceJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Barber service is invalid"));
    }

    @Test
    void returnsNotFoundWhenCreatingServiceForMissingBarber() throws Exception {
        when(barberServiceOfferingService.create(any(BarberServiceCreateRequest.class)))
                .thenThrow(new BarberNotFoundException(999L));

        mockMvc.perform(post("/api/barber-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validServiceJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Barber not found with id: 999"));
    }

    @Test
    void returnsServicesForBarber() throws Exception {
        when(barberServiceOfferingService.findByBarber(1L)).thenReturn(List.of(
                new BarberServiceResponse(
                        10L, 1L, "Ali Rezaei", "Haircut", 30, 400000L
                ),
                new BarberServiceResponse(
                        11L, 1L, "Ali Rezaei", "Beard", 30, 200000L
                )
        ));

        mockMvc.perform(get("/api/barber-services").param("barberId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("Haircut"))
                .andExpect(jsonPath("$[0].durationMinutes").value(30))
                .andExpect(jsonPath("$[0].price").value(400000))
                .andExpect(jsonPath("$[1].id").value(11))
                .andExpect(jsonPath("$[1].name").value("Beard"));

        verify(barberServiceOfferingService).findByBarber(1L);
    }

    @Test
    void returnsEmptyListWhenBarberHasNoServices() throws Exception {
        when(barberServiceOfferingService.findByBarber(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/barber-services").param("barberId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(barberServiceOfferingService).findByBarber(1L);
    }

    @Test
    void returnsNotFoundWhenListingServicesForMissingBarber() throws Exception {
        when(barberServiceOfferingService.findByBarber(999L))
                .thenThrow(new BarberNotFoundException(999L));

        mockMvc.perform(get("/api/barber-services").param("barberId", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Barber not found with id: 999"));

        verify(barberServiceOfferingService).findByBarber(999L);
    }

    private void assertInvalidRequestWithoutServiceCall(String content) throws Exception {
        mockMvc.perform(post("/api/barber-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(barberServiceOfferingService);
    }

    private String validServiceJson() {
        return """
                {
                  "barberId": 1,
                  "name": "Haircut",
                  "durationMinutes": 30,
                  "price": 400000
                }
                """;
    }
}
