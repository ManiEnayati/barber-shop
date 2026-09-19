package com.example.barbershop;

import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.repository.UserRepository;
import com.example.barbershop.service.DevelopmentSmsSender;
import com.example.barbershop.service.SmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "app.dev.admin-phone=+989001234567")
@ActiveProfiles("dev")
class DevelopmentAdminInitializerIntegrationTests {

    @Autowired private UserRepository userRepository;
    @Autowired private SmsSender smsSender;

    @Test
    void devProfileCreatesVerifiedCustomerAdminAndUsesDevelopmentSms() {
        User admin = userRepository.findByPhone("+989001234567").orElseThrow();

        assertTrue(admin.isPhoneVerified());
        assertEquals(Set.of(UserRole.CUSTOMER, UserRole.ADMIN), admin.getRoles());
        assertInstanceOf(DevelopmentSmsSender.class, smsSender);
    }
}
