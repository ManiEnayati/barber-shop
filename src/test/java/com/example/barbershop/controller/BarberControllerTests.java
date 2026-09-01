package com.example.barbershop.controller;

import com.example.barbershop.dto.BarberCreateRequest;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.service.BarberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
}
