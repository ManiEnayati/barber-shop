package com.example.barbershop.entity;

import com.example.barbershop.exception.AppointmentCannotBeCancelledException;
import com.example.barbershop.exception.AppointmentCannotBeRescheduledException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppointmentTests {

    @Test
    void newAppointmentStartsBooked() {
        Appointment appointment = appointment();

        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
    }

    @Test
    void cancellingBookedAppointmentChangesStatusToCancelled() {
        Appointment appointment = appointment();
        Customer customer = appointment.getCustomer();

        appointment.cancel();

        assertAll(
                () -> assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus()),
                () -> assertSame(customer, appointment.getCustomer())
        );
    }

    @Test
    void cancellingCancelledAppointmentIsIdempotent() {
        Appointment appointment = appointment();
        appointment.cancel();

        appointment.cancel();

        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
    }

    @Test
    void completedAppointmentCannotBeCancelled() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.COMPLETED);

        AppointmentCannotBeCancelledException exception = assertThrows(
                AppointmentCannotBeCancelledException.class,
                appointment::cancel
        );

        assertEquals("Appointment cannot be cancelled", exception.getMessage());
    }

    @Test
    void reschedulingBookedAppointmentChangesServiceDateAndTime() {
        Appointment appointment = appointment();
        BarberServiceOffering newService = service(
                appointment.getBarber(),
                "Hair + Beard",
                60
        );
        LocalDate newDate = LocalDate.of(2026, 9, 11);
        LocalTime newTime = LocalTime.of(11, 0);
        Customer customer = appointment.getCustomer();

        appointment.reschedule(newService, newDate, newTime);

        assertAll(
                () -> assertSame(newService, appointment.getServiceOffering()),
                () -> assertEquals(newDate, appointment.getDate()),
                () -> assertEquals(newTime, appointment.getTime()),
                () -> assertSame(customer, appointment.getCustomer()),
                () -> assertEquals(AppointmentStatus.BOOKED, appointment.getStatus())
        );
    }

    @Test
    void completedCancelledAndNoShowAppointmentsCannotBeRescheduled() {
        assertCannotReschedule(AppointmentStatus.COMPLETED);
        assertCannotReschedule(AppointmentStatus.CANCELLED);
        assertCannotReschedule(AppointmentStatus.NO_SHOW);
    }

    private void assertCannotReschedule(AppointmentStatus status) {
        Appointment appointment = appointmentWithStatus(status);

        AppointmentCannotBeRescheduledException exception = assertThrows(
                AppointmentCannotBeRescheduledException.class,
                () -> appointment.reschedule(
                        appointment.getServiceOffering(),
                        appointment.getDate().plusDays(1),
                        appointment.getTime()
                )
        );

        assertEquals("Appointment cannot be rescheduled", exception.getMessage());
    }

    private Appointment appointmentWithStatus(AppointmentStatus status) {
        Appointment appointment = appointment();
        setField(appointment, "status", status);
        return appointment;
    }

    private Appointment appointment() {
        Barber barber = new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        );
        Customer customer = new Customer("Reza Karimi", "09123334444");
        return new Appointment(
                barber,
                service(barber, "Haircut", 30),
                customer,
                LocalDate.of(2026, 9, 10),
                LocalTime.of(10, 0)
        );
    }

    private BarberServiceOffering service(
            Barber barber,
            String name,
            int durationMinutes
    ) {
        return new BarberServiceOffering(barber, name, durationMinutes, 400000L);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
