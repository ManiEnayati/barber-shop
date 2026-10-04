package com.example.barbershop.entity;

import com.example.barbershop.exception.AppointmentCannotBeCancelledException;
import com.example.barbershop.exception.AppointmentCannotBeCompletedException;
import com.example.barbershop.exception.AppointmentCannotBeDelayedException;
import com.example.barbershop.exception.AppointmentCannotBeMarkedArrivedException;
import com.example.barbershop.exception.AppointmentCannotBeMarkedNoShowException;
import com.example.barbershop.exception.AppointmentCannotBeRescheduledException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

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
    void newCustomerAppointmentHasOneNormalRescheduleOpportunity() {
        Appointment appointment = appointment();

        assertAll(
                () -> assertEquals(true, appointment.isRescheduleAvailable()),
                () -> assertEquals(false,
                        appointment.isBarberDelayRemedyAvailable())
        );
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
                () -> assertNull(appointment.getGuestPhone()),
                () -> assertEquals(BookingConfirmationStatus.NOT_REQUIRED,
                        appointment.getConfirmationStatus()),
                () -> assertEquals(false, appointment.isCustomerAccepted()),
                () -> assertEquals(false, appointment.isRescheduleAvailable()),
                () -> assertEquals(false,
                        appointment.isBarberDelayRemedyAvailable())
        );
    }

    @Test
    void claimByLinksCustomerAndClearsGuestIdentityOnly() {
        Barber barber = new Barber("Ali", "09120000000",
                LocalTime.of(10, 0), LocalTime.of(18, 0));
        BarberServiceOffering offering = service(barber, "Haircut", 30);
        Appointment appointment = new Appointment(
                barber,
                offering,
                "Walk-in",
                "+989121234567",
                LocalDate.of(2026, 9, 10),
                LocalTime.of(10, 0)
        );
        Customer customer = new Customer("Reza", "+989121234567");

        appointment.claimBy(customer);

        assertAll(
                () -> assertSame(customer, appointment.getCustomer()),
                () -> assertNull(appointment.getGuestName()),
                () -> assertNull(appointment.getGuestPhone()),
                () -> assertEquals(AppointmentStatus.BOOKED, appointment.getStatus()),
                () -> assertEquals(
                        BookingConfirmationStatus.NOT_REQUIRED,
                        appointment.getConfirmationStatus()
                ),
                () -> assertSame(offering, appointment.getServiceOffering()),
                () -> assertSame(barber, appointment.getBarber()),
                () -> assertEquals(LocalDate.of(2026, 9, 10), appointment.getDate()),
                () -> assertEquals(LocalTime.of(10, 0), appointment.getTime()),
                () -> assertEquals(false, appointment.isCustomerAccepted())
        );
    }

    @Test
    void registeredSelfBookingIsConfirmedWithoutChangingLifecycle() {
        Appointment appointment = appointment();

        assertAll(
                () -> assertEquals(BookingConfirmationStatus.CONFIRMED,
                        appointment.getConfirmationStatus()),
                () -> assertEquals(AppointmentStatus.BOOKED, appointment.getStatus()),
                () -> assertEquals(BookingSource.CUSTOMER,
                        appointment.getBookingSource()),
                () -> assertEquals(true, appointment.isCustomerAccepted())
        );
    }

    @Test
    void barberBookingCanBeConfirmedOrRejectedSeparatelyFromLifecycle() {
        Appointment confirmed = barberBooking();
        Appointment rejected = barberBooking();

        assertEquals(BookingConfirmationStatus.PENDING,
                confirmed.getConfirmationStatus());
        confirmed.confirmBooking();
        rejected.rejectBooking();

        assertAll(
                () -> assertEquals(BookingConfirmationStatus.CONFIRMED,
                        confirmed.getConfirmationStatus()),
                () -> assertEquals(BookingConfirmationStatus.REJECTED,
                        rejected.getConfirmationStatus()),
                () -> assertEquals(true, confirmed.isCustomerAccepted()),
                () -> assertEquals(false, rejected.isCustomerAccepted()),
                () -> assertEquals(AppointmentStatus.BOOKED, confirmed.getStatus()),
                () -> assertEquals(AppointmentStatus.BOOKED, rejected.getStatus())
        );
    }

    @Test
    void barberManualBookingIsActiveWithoutImplyingCustomerConsent() {
        Appointment seed = appointment();

        Appointment manual = Appointment.barberManualBooking(
                seed.getBarber(), seed.getServiceOffering(), seed.getCustomer(),
                seed.getDate(), seed.getTime());

        assertAll(
                () -> assertEquals(AppointmentStatus.BOOKED, manual.getStatus()),
                () -> assertEquals(BookingSource.BARBER, manual.getBookingSource()),
                () -> assertEquals(BookingConfirmationStatus.CONFIRMED,
                        manual.getConfirmationStatus()),
                () -> assertEquals(false, manual.isCustomerAccepted())
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
        appointment.cancel(CancellationReason.BARBER_REQUEST, "Emergency");
        appointment.cancel();

        assertAll(
                () -> assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus()),
                () -> assertEquals(CancellationReason.BARBER_REQUEST,
                        appointment.getCancellationReason()),
                () -> assertEquals("Emergency", appointment.getCancellationNote())
        );
    }

    @Test
    void cancelledAppointmentRejectsDifferentReasonButAcceptsSameReason() {
        Appointment appointment = appointment();
        appointment.cancel(CancellationReason.BARBER_REQUEST, "Emergency");
        appointment.cancel(CancellationReason.BARBER_REQUEST, "Changed note");

        assertEquals("Emergency", appointment.getCancellationNote());
        assertThrows(AppointmentCannotBeCancelledException.class,
                () -> appointment.cancel(CancellationReason.CUSTOMER_REQUEST, "Changed reason"));
        assertEquals(CancellationReason.BARBER_REQUEST,
                appointment.getCancellationReason());
    }

    @Test
    void policyClassifiedCancellationReasonsAreReservedForCustomerFlow() {
        Appointment appointment = appointment();

        for (CancellationReason reason : List.of(
                CancellationReason.CUSTOMER_EARLY,
                CancellationReason.CUSTOMER_LATE,
                CancellationReason.BARBER_DELAY)) {
            assertThrows(AppointmentCannotBeCancelledException.class,
                    () -> appointment.cancel(reason, "Policy result"));
        }
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
    void barberCancellationRequiresReasonAndBookedState() {
        Appointment appointment = appointment();

        assertThrows(AppointmentCannotBeCancelledException.class,
                () -> appointment.cancelByBarber(null));
        assertThrows(AppointmentCannotBeCancelledException.class,
                () -> appointment.cancelByBarber("  "));
        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());

        appointment.cancelByBarber(" emergency ");
        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        assertEquals(CancellationReason.BARBER_REQUEST, appointment.getCancellationReason());
        assertEquals("emergency", appointment.getCancellationNote());
        assertThrows(AppointmentCannotBeCancelledException.class,
                () -> appointment.cancelByBarber("emergency"));
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
    void markingArrivedAppointmentArrivedIsRejected() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.ARRIVED);

        assertThrows(AppointmentCannotBeMarkedArrivedException.class, appointment::arrive);

        assertEquals(AppointmentStatus.ARRIVED, appointment.getStatus());
    }

    @Test
    void arrivedAppointmentCanBeCompleted() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.ARRIVED);

        appointment.complete();

        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
    }

    @Test
    void completingCompletedAppointmentIsRejected() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.COMPLETED);

        assertThrows(AppointmentCannotBeCompletedException.class, appointment::complete);

        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
    }

    @Test
    void bookedAppointmentCanBeMarkedNoShow() {
        Appointment appointment = appointment();

        appointment.markNoShow();

        assertEquals(AppointmentStatus.NO_SHOW, appointment.getStatus());
    }

    @Test
    void markingNoShowAppointmentNoShowIsRejected() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.NO_SHOW);

        assertThrows(AppointmentCannotBeMarkedNoShowException.class,
                appointment::markNoShow);

        assertEquals(AppointmentStatus.NO_SHOW, appointment.getStatus());
    }

    @Test
    void delaySetsExpectedArrivalWithoutChangingScheduledTimes() {
        Appointment appointment = appointment();
        LocalDate date = appointment.getDate();
        LocalTime time = appointment.getTime();

        appointment.updateDelay(25);

        assertAll(
                () -> assertEquals(25, appointment.getDelayMinutes()),
                () -> assertEquals(LocalDateTime.of(date, time).plusMinutes(25),
                        appointment.getExpectedArrivalTime()),
                () -> assertEquals(date, appointment.getDate()),
                () -> assertEquals(time, appointment.getTime())
        );
    }

    @Test
    void removingDelayClearsOperationalDelayState() {
        Appointment appointment = appointment();
        appointment.updateDelay(15);

        appointment.removeDelay();

        assertAll(
                () -> assertNull(appointment.getDelayMinutes()),
                () -> assertNull(appointment.getExpectedArrivalTime()),
                () -> assertEquals(15, appointment.getMaxBarberDelayMinutes())
        );
    }

    @Test
    void maximumBarberDelayNeverDecreasesOrClears() {
        Appointment appointment = appointment();

        appointment.updateDelay(10);
        appointment.updateDelay(40);
        appointment.updateDelay(20);
        appointment.removeDelay();
        appointment.rescheduleByCustomer(
                appointment.getDate().plusDays(1), appointment.getTime());

        assertEquals(40, appointment.getMaxBarberDelayMinutes());
    }

    @Test
    void removingDelayDoesNotRevokeGrantedCustomerRemedy() {
        Appointment appointment = appointment();
        appointment.rescheduleByCustomer(
                appointment.getDate().plusDays(1), appointment.getTime());
        appointment.updateDelay(15);

        appointment.removeDelay();

        assertAll(
                () -> assertEquals(true, appointment.isRescheduleAvailable()),
                () -> assertEquals(true,
                        appointment.isBarberDelayRemedyAvailable()),
                () -> assertNull(appointment.getDelayMinutes()),
                () -> assertNull(appointment.getExpectedArrivalTime())
        );
    }

    @Test
    void customerRescheduleConsumesOpportunityAndClearsDelayState() {
        Appointment appointment = appointment();
        appointment.updateDelay(20);

        appointment.rescheduleByCustomer(
                appointment.getDate().plusDays(1), LocalTime.of(11, 0));

        assertAll(
                () -> assertEquals(false, appointment.isRescheduleAvailable()),
                () -> assertEquals(false,
                        appointment.isBarberDelayRemedyAvailable()),
                () -> assertNull(appointment.getDelayMinutes()),
                () -> assertNull(appointment.getExpectedArrivalTime())
        );
        assertThrows(AppointmentCannotBeRescheduledException.class,
                () -> appointment.rescheduleByCustomer(
                        appointment.getDate().plusDays(1), LocalTime.NOON));
    }

    @Test
    void noShowDeadlineIncludesGracePeriod() {
        Appointment appointment = appointment();
        LocalDateTime scheduledStart = LocalDateTime.of(
                appointment.getDate(), appointment.getTime());

        assertEquals(scheduledStart.plusMinutes(15),
                appointment.getNoShowDeadline(15));
    }

    @Test
    void delayedNoShowDeadlineIncludesDelayAndGracePeriod() {
        Appointment appointment = appointment();
        LocalDateTime scheduledStart = LocalDateTime.of(
                appointment.getDate(), appointment.getTime());
        appointment.updateDelay(20);

        assertEquals(scheduledStart.plusMinutes(35),
                appointment.getNoShowDeadline(15));
    }

    @Test
    void terminalAppointmentsRejectDelayChanges() {
        for (AppointmentStatus status : new AppointmentStatus[]{
                AppointmentStatus.ARRIVED,
                AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.NO_SHOW
        }) {
            Appointment appointment = appointmentWithStatus(status);

            assertThrows(AppointmentCannotBeDelayedException.class,
                    () -> appointment.updateDelay(10));
            assertNull(appointment.getDelayMinutes());
            assertNull(appointment.getExpectedArrivalTime());
        }
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

    private Appointment barberBooking() {
        Appointment existing = appointment();
        return new Appointment(existing.getBarber(), existing.getServiceOffering(),
                existing.getCustomer(), BookingSource.BARBER,
                existing.getDate(), existing.getTime());
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
