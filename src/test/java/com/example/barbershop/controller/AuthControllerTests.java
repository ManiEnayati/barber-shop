package com.example.barbershop.controller;

import com.example.barbershop.config.SecurityConfig;
import com.example.barbershop.dto.RegisterRequest;
import com.example.barbershop.dto.UserResponse;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.exception.EmailAlreadyExistsException;
import com.example.barbershop.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registersUserAndDoesNotExposePassword() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "test@test.com",
                "123456",
                UserRole.CUSTOMER
        );
        when(authService.register(request)).thenReturn(new UserResponse(
                1L,
                "test@test.com",
                UserRole.CUSTOMER
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(authService).register(request);
    }

    @Test
    void returnsConflictForDuplicateEmail() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    void rejectsBlankEmail() throws Exception {
        assertInvalidRequest("""
                {
                  "email": " ",
                  "password": "123456",
                  "role": "CUSTOMER"
                }
                """);
    }

    @Test
    void rejectsBlankPassword() throws Exception {
        assertInvalidRequest("""
                {
                  "email": "test@test.com",
                  "password": " ",
                  "role": "CUSTOMER"
                }
                """);
    }

    @Test
    void rejectsMissingRole() throws Exception {
        assertInvalidRequest("""
                {
                  "email": "test@test.com",
                  "password": "123456"
                }
                """);
    }

    private void assertInvalidRequest(String content) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    private String validRequestJson() {
        return """
                {
                  "email": "test@test.com",
                  "password": "123456",
                  "role": "CUSTOMER"
                }
                """;
    }
}
