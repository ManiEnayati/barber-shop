package com.example.barbershop.repository;

import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
class BarberServiceOfferingRepositoryTests {

    @Autowired
    private BarberServiceOfferingRepository barberServiceOfferingRepository;

    @Autowired
    private BarberRepository barberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsBarberServiceOffering() {
        Barber barber = barberRepository.saveAndFlush(new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
        BarberServiceOffering savedService =
                barberServiceOfferingRepository.saveAndFlush(
                        new BarberServiceOffering(
                                barber,
                                "Haircut",
                                30,
                                400000L
                        )
                );
        Long serviceId = savedService.getId();
        entityManager.clear();

        BarberServiceOffering reloadedService =
                barberServiceOfferingRepository.findById(serviceId).orElseThrow();

        assertAll(
                () -> assertNotNull(reloadedService.getId()),
                () -> assertEquals(barber.getId(), reloadedService.getBarber().getId()),
                () -> assertEquals("Ali Rezaei", reloadedService.getBarber().getName()),
                () -> assertEquals("Haircut", reloadedService.getName()),
                () -> assertEquals(30, reloadedService.getDurationMinutes()),
                () -> assertEquals(400000L, reloadedService.getPrice())
        );
    }

    @Test
    void findsOnlyServicesBelongingToRequestedBarber() {
        Barber requestedBarber = barberRepository.save(new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
        Barber otherBarber = barberRepository.save(new Barber(
                "Sara Ahmadi",
                "09121111111",
                LocalTime.of(9, 30),
                LocalTime.of(17, 0)
        ));
        barberServiceOfferingRepository.saveAllAndFlush(List.of(
                new BarberServiceOffering(
                        requestedBarber, "Haircut", 30, 400000L
                ),
                new BarberServiceOffering(
                        requestedBarber, "Beard", 30, 200000L
                ),
                new BarberServiceOffering(
                        otherBarber, "Hair + Beard", 60, 550000L
                )
        ));
        entityManager.clear();

        List<BarberServiceOffering> services =
                barberServiceOfferingRepository.findByBarberId(requestedBarber.getId());
        Set<String> serviceNames = services.stream()
                .map(BarberServiceOffering::getName)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(2, services.size()),
                () -> assertEquals(Set.of("Haircut", "Beard"), serviceNames),
                () -> assertEquals(
                        Set.of(requestedBarber.getId()),
                        services.stream()
                                .map(service -> service.getBarber().getId())
                                .collect(Collectors.toSet())
                )
        );
    }
}
