package com.example.barbershop.repository;

import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class BarberRepositoryTests {

    @Autowired
    private BarberRepository barberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsBarberWorkingHours() {
        Barber savedBarber = barberRepository.saveAndFlush(new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
        Long barberId = savedBarber.getId();
        entityManager.clear();

        Barber reloadedBarber = barberRepository.findById(barberId).orElseThrow();

        assertAll(
                () -> assertEquals(LocalTime.of(10, 0), reloadedBarber.getWorkStartTime()),
                () -> assertEquals(LocalTime.of(18, 0), reloadedBarber.getWorkEndTime())
        );
    }

    @Test
    void findsBarberByLinkedUser() {
        User user = new User("+989121111111");
        user.verifyPhone();
        user.approveBarber();
        userRepository.saveAndFlush(user);
        Barber barber = barberRepository.saveAndFlush(new Barber(
                user,
                "Ali Rezaei",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
        entityManager.clear();

        Barber found = barberRepository.findByUserId(user.getId()).orElseThrow();

        assertAll(
                () -> assertEquals(barber.getId(), found.getId()),
                () -> assertEquals(user.getId(), found.getUser().getId()),
                () -> assertEquals(user.getPhone(), found.getPhone())
        );
    }
}
