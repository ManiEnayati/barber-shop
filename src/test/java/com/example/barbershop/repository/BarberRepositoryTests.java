package com.example.barbershop.repository;

import com.example.barbershop.entity.Barber;
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
}
