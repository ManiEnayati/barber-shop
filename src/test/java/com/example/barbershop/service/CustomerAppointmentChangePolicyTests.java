package com.example.barbershop.service;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.CancellationReason;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.AppointmentCannotBeRescheduledException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerAppointmentChangePolicyTests {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
    private final CustomerAppointmentChangePolicy policy =
            new CustomerAppointmentChangePolicy(120);

    @Test
    void exactCutoffBoundaryIsNotRestricted() {
        Appointment appointment = appointmentAt(LocalTime.of(18, 0));
        LocalDateTime boundary = LocalDateTime.of(DATE, LocalTime.of(16, 0));

        assertFalse(policy.isInsideRestrictedWindow(appointment, boundary));
        assertDoesNotThrow(() -> policy.requireRescheduleAllowed(
                appointment, boundary));
        assertEquals(CancellationReason.CUSTOMER_EARLY,
                policy.classifyCancellation(appointment, boundary));
    }

    @Test
    void oneSecondAfterCutoffIsRestricted() {
        Appointment appointment = appointmentAt(LocalTime.of(18, 0));
        LocalDateTime inside = LocalDateTime.of(
                DATE, LocalTime.of(16, 0, 1));

        assertTrue(policy.isInsideRestrictedWindow(appointment, inside));
        assertThrows(AppointmentCannotBeRescheduledException.class,
                () -> policy.requireRescheduleAllowed(appointment, inside));
        assertEquals(CancellationReason.CUSTOMER_LATE,
                policy.classifyCancellation(appointment, inside));
    }

    @Test
    void barberDelayRemedyWaivesCutoffAndControlsCancellationReason() {
        Appointment appointment = appointmentAt(LocalTime.of(18, 0));
        appointment.updateDelay(20);
        LocalDateTime inside = LocalDateTime.of(DATE, LocalTime.of(17, 59));

        assertDoesNotThrow(() -> policy.requireRescheduleAllowed(
                appointment, inside));
        assertEquals(CancellationReason.BARBER_DELAY,
                policy.classifyCancellation(appointment, inside));
    }

    @Test
    void cutoffAlwaysUsesCurrentAppointmentSchedule() {
        Appointment appointment = appointmentAt(LocalTime.of(18, 0));
        appointment.rescheduleByCustomer(DATE.plusDays(1), LocalTime.of(18, 0));

        assertFalse(policy.isInsideRestrictedWindow(
                appointment,
                LocalDateTime.of(DATE, LocalTime.of(16, 0, 1))
        ));
    }

    @Test
    void negativeCutoffIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CustomerAppointmentChangePolicy(-1));
    }

    private Appointment appointmentAt(LocalTime time) {
        Barber barber = new Barber("Ali", "09120000000",
                LocalTime.of(10, 0), LocalTime.of(20, 0));
        BarberServiceOffering service = new BarberServiceOffering(
                barber, "Haircut", 30, 400000L);
        return new Appointment(
                barber,
                service,
                new Customer("Reza", "09123334444"),
                DATE,
                time
        );
    }
}
