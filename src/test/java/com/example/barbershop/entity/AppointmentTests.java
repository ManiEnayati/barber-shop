package com.example.barbershop.entity;

import com.example.barbershop.exception.AppointmentCannotBeCancelledException;
import com.example.barbershop.exception.AppointmentCannotBeCompletedException;
import com.example.barbershop.exception.AppointmentCannotBeMarkedArrivedException;
import com.example.barbershop.exception.AppointmentCannotBeMarkedNoShowException;
import com.example.barbershop.exception.AppointmentCannotBeRescheduledException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class AppointmentTests {

    @Test
    void newAppointmentStartsBooked() {
        Appointment appointment = appointment();

        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
    }

    @Test
    void guestAppointmentHasNoCustomer() {
        Barber barber = new Barber("Ali", "09120000000",
                LocalTime.of(10, 0), LocalTime.of(18, 0));
        Appointment appointment = new Appointment(barber,
                service(barber, "Haircut", 30), "  Walk-in  ", null,
                LocalDate.of(2026, 9, 10), LocalTime.of(10, 0));

        assertAll(
                () -> assertNull(appointment.getCustomer()),
                () -> assertEquals("Walk-in", appointment.getGuestName()),
                () -> assertNull(appointment.getGuestPhone())
        );
    }

    @Test
    void rejectsAppointmentWithoutCustomerOrGuestName() {
        Barber barber = new Barber("Ali", "09120000000",
                LocalTime.of(10, 0), LocalTime.of(18, 0));
        BarberServiceOffering offering = service(barber, "Haircut", 30);

        assertThrows(IllegalArgumentException.class, () -> new Appointment(
                barber, offering, (Customer) null,
                LocalDate.of(2026, 9, 10), LocalTime.of(10, 0)));
        assertThrows(IllegalArgumentException.class, () -> new Appointment(
                barber, offering, "  ", null,
                LocalDate.of(2026, 9, 10), LocalTime.of(10, 0)));
    }

    @Test
    void cancellationReasonIsStoredOnlyOnFirstCancellation() {
        Appointment appointment = appointment();
        appointment.cancel(CancellationReason.BARBER_DELAY, "Late arrival");
        appointment.cancel();

        assertAll(
                () -> assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus()),
                () -> assertEquals(CancellationReason.BARBER_DELAY,
                        appointment.getCancellationReason()),
                () -> assertEquals("Late arrival", appointment.getCancellationNote())
        );
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
    void bookedAppointmentCanBeMarkedArrived() {
        Appointment appointment = appointment();

        appointment.arrive();

        assertEquals(AppointmentStatus.ARRIVED, appointment.getStatus());
    }

    @Test
    void markingArrivedAppointmentArrivedIsIdempotent() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.ARRIVED);

        appointment.arrive();

        assertEquals(AppointmentStatus.ARRIVED, appointment.getStatus());
    }

    @Test
    void arrivedAppointmentCanBeCompleted() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.ARRIVED);

        appointment.complete();

        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
    }

    @Test
    void completingCompletedAppointmentIsIdempotent() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.COMPLETED);

        appointment.complete();

        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
    }

    @Test
    void bookedAppointmentCanBeMarkedNoShow() {
        Appointment appointment = appointment();

        appointment.markNoShow();

        assertEquals(AppointmentStatus.NO_SHOW, appointment.getStatus());
    }

    @Test
    void markingNoShowAppointmentNoShowIsIdempotent() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.NO_SHOW);

        appointment.markNoShow();

        assertEquals(AppointmentStatus.NO_SHOW, appointment.getStatus());
    }

    @Test
    void bookedAppointmentCannotBeCompletedDirectly() {
        Appointment appointment = appointment();

        AppointmentCannotBeCompletedException exception = assertThrows(
                AppointmentCannotBeCompletedException.class,
                appointment::complete
        );

        assertEquals("Appointment cannot be completed", exception.getMessage());
    }

    @Test
    void arrivedAppointmentCannotBeMarkedNoShow() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.ARRIVED);

        AppointmentCannotBeMarkedNoShowException exception = assertThrows(
                AppointmentCannotBeMarkedNoShowException.class,
                appointment::markNoShow
        );

        assertEquals("Appointment cannot be marked as no-show", exception.getMessage());
    }

    @Test
    void cancelledAppointmentCannotEnterAnotherLifecycleState() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.CANCELLED);

        assertCannotArrive(appointment);
        assertCannotComplete(appointment);
        assertCannotMarkNoShow(appointment);
    }

    @Test
    void completedAndNoShowAppointmentsCannotBeMarkedArrived() {
        assertCannotArrive(appointmentWithStatus(AppointmentStatus.COMPLETED));
        assertCannotArrive(appointmentWithStatus(AppointmentStatus.NO_SHOW));
    }

    @Test
    void arrivedCompletedAndNoShowAppointmentsCannotBeCancelled() {
        assertCannotCancel(AppointmentStatus.ARRIVED);
        assertCannotCancel(AppointmentStatus.COMPLETED);
        assertCannotCancel(AppointmentStatus.NO_SHOW);
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
    void nonBookedAppointmentsCannotBeRescheduled() {
        assertCannotReschedule(AppointmentStatus.ARRIVED);
        assertCannotReschedule(AppointmentStatus.COMPLETED);
        assertCannotReschedule(AppointmentStatus.CANCELLED);
        assertCannotReschedule(AppointmentStatus.NO_SHOW);
    }

    private void assertCannotCancel(AppointmentStatus status) {
        Appointment appointment = appointmentWithStatus(status);

        AppointmentCannotBeCancelledException exception = assertThrows(
                AppointmentCannotBeCancelledException.class,
                appointment::cancel
        );

        assertEquals("Appointment cannot be cancelled", exception.getMessage());
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

    private void assertCannotArrive(Appointment appointment) {
        AppointmentCannotBeMarkedArrivedException exception = assertThrows(
                AppointmentCannotBeMarkedArrivedException.class,
                appointment::arrive
        );
        assertEquals("Appointment cannot be marked as arrived", exception.getMessage());
    }

    private void assertCannotComplete(Appointment appointment) {
        AppointmentCannotBeCompletedException exception = assertThrows(
                AppointmentCannotBeCompletedException.class,
                appointment::complete
        );
        assertEquals("Appointment cannot be completed", exception.getMessage());
    }

    private void assertCannotMarkNoShow(Appointment appointment) {
        AppointmentCannotBeMarkedNoShowException exception = assertThrows(
                AppointmentCannotBeMarkedNoShowException.class,
                appointment::markNoShow
        );
        assertEquals("Appointment cannot be marked as no-show", exception.getMessage());
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
