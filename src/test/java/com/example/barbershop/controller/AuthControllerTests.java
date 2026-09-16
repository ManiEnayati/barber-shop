package com.example.barbershop.controller;

import com.example.barbershop.config.SecurityConfig;
import com.example.barbershop.dto.UserResponse;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.exception.InvalidPhoneOtpException;
import com.example.barbershop.service.PhoneOtpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PhoneOtpService phoneOtpService;

    @Test
    void requestsOtpWithoutReturningCode() throws Exception {
        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"09121234567\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verify(phoneOtpService).requestOtp("09121234567");
    }

    @Test
    void verifiesOtpWithoutExposingPasswordEmailOrOtp() throws Exception {
        when(phoneOtpService.verifyOtp("09121234567", "123456"))
                .thenReturn(new UserResponse(
                        1L,
                        "+989121234567",
                        true,
                        Set.of(UserRole.CUSTOMER)
                ));

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "09121234567",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.phone").value("+989121234567"))
                .andExpect(jsonPath("$.phoneVerified").value(true))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.otp").doesNotExist());
    }

    @Test
    void publicVerifyRequestCannotAssignBarberRole() throws Exception {
        when(phoneOtpService.verifyOtp("09121234567", "123456"))
                .thenReturn(new UserResponse(
                        1L,
                        "+989121234567",
                        true,
                        Set.of(UserRole.CUSTOMER)
                ));

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "09121234567",
                                  "code": "123456",
                                  "roles": ["BARBER"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));

        verify(phoneOtpService).verifyOtp("09121234567", "123456");
    }

    @Test
    void returnsBadRequestForInvalidOtp() throws Exception {
        when(phoneOtpService.verifyOtp("09121234567", "123456"))
                .thenThrow(new InvalidPhoneOtpException("OTP code is incorrect"));

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "09121234567",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("OTP code is incorrect"));
    }

    @Test
    void rejectsMalformedOtpBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "09121234567",
                                  "code": "12345"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(phoneOtpService);
    }

    @Test
    void oldRegistrationEndpointIsRemoved() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@test.com",
                                  "password": "123456",
                                  "role": "BARBER"
                                }
                                """))
                .andExpect(status().isNotFound());

        verifyNoInteractions(phoneOtpService);
    }
}
