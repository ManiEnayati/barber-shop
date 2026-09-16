package com.example.barbershop.repository;

import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import jakarta.persistence.EntityManager;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UserRepositoryTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsFindsAndChecksVerifiedUserByPhone() {
        User user = new User("+989121234567");
        user.verifyPhone();
        User saved = userRepository.saveAndFlush(user);
        entityManager.clear();

        User found = userRepository.findByPhone("+989121234567").orElseThrow();
        assertNotNull(saved.getId());
        assertEquals(saved.getId(), found.getId());
        assertEquals("+989121234567", found.getPhone());
        assertTrue(found.isPhoneVerified());
        assertEquals(Set.of(UserRole.CUSTOMER), found.getRoles());
        assertTrue(userRepository.existsByPhone("+989121234567"));
        assertFalse(userRepository.existsByPhone("+989111111111"));
    }

    @Test
    void persistsCustomerAndBarberRolesForSameUser() {
        User user = new User("+989121234567");
        user.approveBarber();
        Long userId = userRepository.saveAndFlush(user).getId();
        entityManager.clear();

        User found = userRepository.findById(userId).orElseThrow();

        assertEquals(
                Set.of(UserRole.CUSTOMER, UserRole.BARBER),
                found.getRoles()
        );
    }
}
