package com.example.barbershop.repository;

import com.example.barbershop.entity.PhoneOtp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
class PhoneOtpRepositoryTests {

    private static final String PHONE = "+989121234567";

    @Autowired
    private PhoneOtpRepository phoneOtpRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsLatestOtpAndPersistsItsState() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 5, 12, 0);
        phoneOtpRepository.save(new PhoneOtp(
                PHONE,
                "111111",
                now.plusMinutes(5)
        ));
        PhoneOtp latest = new PhoneOtp(
                PHONE,
                "222222",
                now,
                now.plusMinutes(5)
        );
        latest.recordFailedAttempt();
        latest.markVerified(now);
        Long latestId = phoneOtpRepository.saveAndFlush(latest).getId();
        entityManager.clear();

        PhoneOtp found = phoneOtpRepository
                .findFirstByPhoneOrderByIdDesc(PHONE)
                .orElseThrow();

        assertEquals(latestId, found.getId());
        assertEquals("222222", found.getCode());
        assertEquals(now, found.getRequestedAt());
        assertEquals(1, found.getAttempts());
        assertNotNull(found.getVerifiedAt());
    }
}
