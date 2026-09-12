package com.example.barbershop.repository;

import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BlockedTime;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
class BlockedTimeRepositoryTests {

    @Autowired
    private BlockedTimeRepository blockedTimeRepository;

    @Autowired
    private BarberRepository barberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsBlockedTimeWithBarberForeignKey() {
        Barber barber = saveBarber("Ali Rezaei");
        BlockedTime saved = blockedTimeRepository.saveAndFlush(new BlockedTime(
                barber,
                LocalDate.of(2026, 9, 12),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                "Lunch"
        ));
        Long id = saved.getId();
        entityManager.clear();

        BlockedTime reloaded = blockedTimeRepository.findById(id).orElseThrow();
        Number barberId = (Number) entityManager.createNativeQuery(
                        "select barber_id from blocked_times where id = :id"
                )
                .setParameter("id", id)
                .getSingleResult();

        assertAll(
                () -> assertNotNull(reloaded.getId()),
                () -> assertEquals(barber.getId(), reloaded.getBarber().getId()),
                () -> assertEquals(LocalDate.of(2026, 9, 12), reloaded.getDate()),
                () -> assertEquals(LocalTime.of(13, 0), reloaded.getStartTime()),
                () -> assertEquals(LocalTime.of(14, 0), reloaded.getEndTime()),
                () -> assertEquals("Lunch", reloaded.getReason()),
                () -> assertEquals(barber.getId().longValue(), barberId.longValue())
        );
    }

    @Test
    void findsOnlyBlocksForRequestedBarberAndDate() {
        Barber requestedBarber = saveBarber("Ali Rezaei");
        Barber otherBarber = saveBarber("Sara Ahmadi");
        LocalDate requestedDate = LocalDate.of(2026, 9, 12);
        blockedTimeRepository.saveAllAndFlush(List.of(
                new BlockedTime(
                        requestedBarber,
                        requestedDate,
                        LocalTime.of(13, 0),
                        LocalTime.of(14, 0),
                        "Lunch"
                ),
                new BlockedTime(
                        requestedBarber,
                        requestedDate.plusDays(1),
                        LocalTime.of(13, 0),
                        LocalTime.of(14, 0),
                        "Other date"
                ),
                new BlockedTime(
                        otherBarber,
                        requestedDate,
                        LocalTime.of(13, 0),
                        LocalTime.of(14, 0),
                        "Other barber"
                )
        ));
        entityManager.clear();

        List<BlockedTime> results = blockedTimeRepository.findByBarberIdAndDate(
                requestedBarber.getId(), requestedDate
        );

        assertEquals(1, results.size());
        assertEquals("Lunch", results.getFirst().getReason());
    }

    private Barber saveBarber(String name) {
        return barberRepository.save(new Barber(
                name,
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
    }
}
