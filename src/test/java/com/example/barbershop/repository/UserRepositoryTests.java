package com.example.barbershop.repository;

import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UserRepositoryTests {

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsFindsAndChecksUserByEmail() {
        User saved = userRepository.saveAndFlush(new User(
                "test@test.com",
                "$2a$10$encoded-password",
                UserRole.ADMIN
        ));

        User found = userRepository.findByEmail("test@test.com").orElseThrow();
        assertNotNull(saved.getId());
        assertEquals(saved.getId(), found.getId());
        assertEquals("test@test.com", found.getEmail());
        assertEquals("$2a$10$encoded-password", found.getPassword());
        assertEquals(UserRole.ADMIN, found.getRole());
        assertTrue(userRepository.existsByEmail("test@test.com"));
        assertFalse(userRepository.existsByEmail("missing@test.com"));
    }
}
