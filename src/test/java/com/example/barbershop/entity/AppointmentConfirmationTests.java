package com.example.barbershop.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentConfirmationTests {

    @Test
    void codeExpiresAtDeadline() {
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 21, 10, 15);
        AppointmentConfirmation confirmation = confirmation(deadline);

        assertFalse(confirmation.isExpired(deadline.minusNanos(1)));
        assertTrue(confirmation.isExpired(deadline));
    }

    @Test
    void confirmedCodeIsClearedAndCannotBeReused() {
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 21, 10, 15);
        AppointmentConfirmation confirmation = confirmation(deadline);
        LocalDateTime now = deadline.minusMinutes(1);

        assertTrue(confirmation.matchesCode("123456"));
        confirmation.markConfirmed(now);

        assertEquals(now, confirmation.getConfirmedAt());
        assertNull(confirmation.getCode());
        assertFalse(confirmation.matchesCode("123456"));
        assertThrows(IllegalStateException.class,
                () -> confirmation.markConfirmed(now));
    }

    @Test
    void failedAttemptsAreCountedAndReachConfiguredLimit() {
        AppointmentConfirmation confirmation = confirmation(
                LocalDateTime.of(2026, 9, 21, 10, 15));

        for (int attempt = 0; attempt < 5; attempt++) {
            confirmation.recordFailedAttempt();
        }

        assertEquals(5, confirmation.getFailedAttempts());
        assertTrue(confirmation.hasReachedFailedAttemptLimit(5));
    }

    @Test
    void guestCannotHaveCustomerConfirmation() {
        Barber barber = new Barber("Ali", "09120000000",
                LocalTime.of(10, 0), LocalTime.of(18, 0));
        BarberServiceOffering service = new BarberServiceOffering(
                barber, "Haircut", 30, 400000L);
        Appointment guest = new Appointment(barber, service, "Walk-in", null,
                LocalDate.of(2026, 9, 21), LocalTime.of(10, 0));

        assertThrows(IllegalArgumentException.class,
                () -> new AppointmentConfirmation(guest, "123456",
                        LocalDateTime.of(2026, 9, 21, 10, 15)));
    }

    private AppointmentConfirmation confirmation(LocalDateTime deadline) {
        Barber barber = new Barber("Ali", "09120000000",
                LocalTime.of(10, 0), LocalTime.of(18, 0));
        BarberServiceOffering service = new BarberServiceOffering(
                barber, "Haircut", 30, 400000L);
        Customer customer = new Customer("Reza", "09123334444");
        Appointment appointment = new Appointment(barber, service, customer,
                BookingSource.BARBER, LocalDate.of(2026, 9, 21), LocalTime.of(10, 0));
        return new AppointmentConfirmation(appointment, "123456", deadline);
    }
}
