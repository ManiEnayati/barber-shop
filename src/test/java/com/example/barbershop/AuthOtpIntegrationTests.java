package com.example.barbershop;

import com.example.barbershop.entity.PhoneOtp;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.repository.PhoneOtpRepository;
import com.example.barbershop.repository.UserRepository;
import com.example.barbershop.service.SmsSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthOtpIntegrationTests {

    private static final String PHONE = "+989121234567";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PhoneOtpRepository phoneOtpRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private SmsSender smsSender;

    @Test
    void otpApiCreatesVerifiedCustomerAndIgnoresBarberRoleInjection()
            throws Exception {
        mockMvc.perform(post("/api/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"09121234567\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsSender).sendOtp(eq(PHONE), codeCaptor.capture());
        String code = codeCaptor.getValue();
        assertTrue(code.matches("[0-9]{6}"));

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "00989121234567",
                                  "code": "%s",
                                  "roles": ["BARBER"]
                                }
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(PHONE))
                .andExpect(jsonPath("$.phoneVerified").value(true))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.otp").doesNotExist());

        User user = userRepository.findByPhone(PHONE).orElseThrow();
        PhoneOtp otp = phoneOtpRepository
                .findFirstByPhoneOrderByIdDesc(PHONE)
                .orElseThrow();
        assertTrue(user.isPhoneVerified());
        assertEquals(Set.of(UserRole.CUSTOMER), user.getRoles());
        assertEquals(1, userRepository.count());
        assertTrue(otp.isVerified());
    }
}
