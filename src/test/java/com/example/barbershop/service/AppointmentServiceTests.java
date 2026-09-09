package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.AvailableTimeResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.InvalidAppointmentTimeException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTests {

    private static final LocalDate APPOINTMENT_DATE = LocalDate.of(2026, 9, 10);

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private BarberRepository barberRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void createsAppointmentAtBarberOpeningTimeAndReturnsResponse() {
        LocalTime time = LocalTime.of(10, 0);
        AppointmentCreateRequest request = requestAt(time);
        Barber barber = scheduledBarber(LocalTime.of(10, 0), LocalTime.of(18, 0));
        when(barber.getId()).thenReturn(1L);
        when(barber.getName()).thenReturn("Ali Rezaei");
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(appointmentRepository.existsByBarberIdAndDateAndTime(1L, APPOINTMENT_DATE, time))
                .thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentResponse response = appointmentService.create(request);

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(appointmentCaptor.capture());
        Appointment appointmentToSave = appointmentCaptor.getValue();
        assertAll(
                () -> assertSame(barber, appointmentToSave.getBarber()),
                () -> assertEquals(APPOINTMENT_DATE, appointmentToSave.getDate()),
                () -> assertEquals(time, appointmentToSave.getTime()),
                () -> assertEquals("Reza Karimi", appointmentToSave.getClientName()),
                () -> assertEquals(
                        new AppointmentResponse(
                                null,
                                1L,
                                "Ali Rezaei",
                                APPOINTMENT_DATE,
                                time,
                                "Reza Karimi"
                        ),
                        response
                )
        );
    }

    @Test
    void createsAppointmentAtLastLegalSlot() {
        LocalTime time = LocalTime.of(17, 30);
        Barber barber = scheduledBarber(LocalTime.of(10, 0), LocalTime.of(18, 0));
        when(barber.getId()).thenReturn(1L);
        when(barber.getName()).thenReturn("Ali Rezaei");
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(appointmentRepository.existsByBarberIdAndDateAndTime(1L, APPOINTMENT_DATE, time))
                .thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentResponse response = appointmentService.create(requestAt(time));

        assertEquals(time, response.time());
        verify(appointmentRepository).existsByBarberIdAndDateAndTime(
                1L,
                APPOINTMENT_DATE,
                time
        );
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void rejectsTimeBeforeBarberScheduleWithoutCheckingBookingsOrSaving() {
        assertInvalidAppointmentTime(LocalTime.of(9, 30));
    }

    @Test
    void rejectsTimeOutsideThirtyMinuteBoundaryWithoutCheckingBookingsOrSaving() {
        assertInvalidAppointmentTime(LocalTime.of(10, 15));
    }

    @Test
    void rejectsBarberClosingTimeWithoutCheckingBookingsOrSaving() {
        assertInvalidAppointmentTime(LocalTime.of(18, 0));
    }

    @Test
    void throwsWhenBarberDoesNotExistWithoutAppointmentRepositoryInteraction() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                999L,
                APPOINTMENT_DATE,
                LocalTime.of(10, 0),
                "Reza Karimi"
        );
        when(barberRepository.findById(999L)).thenReturn(Optional.empty());

        BarberNotFoundException exception = assertThrows(
                BarberNotFoundException.class,
                () -> appointmentService.create(request)
        );

        assertEquals("Barber not found with id: 999", exception.getMessage());
        verify(barberRepository).findById(999L);
        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void rejectsAlreadyBookedValidSlotWithoutSavingAppointment() {
        LocalTime time = LocalTime.of(14, 30);
        Barber barber = scheduledBarber(LocalTime.of(10, 0), LocalTime.of(18, 0));
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(appointmentRepository.existsByBarberIdAndDateAndTime(1L, APPOINTMENT_DATE, time))
                .thenReturn(true);

        AppointmentSlotAlreadyBookedException exception = assertThrows(
                AppointmentSlotAlreadyBookedException.class,
                () -> appointmentService.create(requestAt(time))
        );

        assertEquals("Appointment slot is already booked", exception.getMessage());
        verify(appointmentRepository).existsByBarberIdAndDateAndTime(
                1L,
                APPOINTMENT_DATE,
                time
        );
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void findsAppointmentsForExistingBarberAndDate() {
        Barber barber = mock(Barber.class);
        when(barber.getId()).thenReturn(1L);
        when(barber.getName()).thenReturn("Ali Rezaei");
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        Appointment morningAppointment = appointment(
                10L,
                barber,
                LocalTime.of(10, 30),
                "Reza Karimi"
        );
        Appointment afternoonAppointment = appointment(
                11L,
                barber,
                LocalTime.of(14, 0),
                "Mina Jafari"
        );
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(List.of(morningAppointment, afternoonAppointment));

        List<AppointmentResponse> responses = appointmentService.findByBarberAndDate(
                1L,
                APPOINTMENT_DATE
        );

        assertEquals(
                List.of(
                        new AppointmentResponse(
                                10L,
                                1L,
                                "Ali Rezaei",
                                APPOINTMENT_DATE,
                                LocalTime.of(10, 30),
                                "Reza Karimi"
                        ),
                        new AppointmentResponse(
                                11L,
                                1L,
                                "Ali Rezaei",
                                APPOINTMENT_DATE,
                                LocalTime.of(14, 0),
                                "Mina Jafari"
                        )
                ),
                responses
        );
        verify(appointmentRepository).findByBarberIdAndDate(1L, APPOINTMENT_DATE);
    }

    @Test
    void returnsEmptyListWhenExistingBarberHasNoAppointmentsOnDate() {
        when(barberRepository.findById(1L)).thenReturn(Optional.of(mock(Barber.class)));
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(List.of());

        List<AppointmentResponse> responses = appointmentService.findByBarberAndDate(
                1L,
                APPOINTMENT_DATE
        );

        assertEquals(List.of(), responses);
        verify(appointmentRepository).findByBarberIdAndDate(1L, APPOINTMENT_DATE);
    }

    @Test
    void throwsWhenFindingAppointmentsForMissingBarber() {
        when(barberRepository.findById(999L)).thenReturn(Optional.empty());

        BarberNotFoundException exception = assertThrows(
                BarberNotFoundException.class,
                () -> appointmentService.findByBarberAndDate(999L, APPOINTMENT_DATE)
        );

        assertEquals("Barber not found with id: 999", exception.getMessage());
        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void returnsScheduleSpecificAvailableSlotsWhenThereAreNoBookings() {
        Barber barber = scheduledBarber(LocalTime.of(10, 0), LocalTime.of(12, 0));
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(List.of());

        List<AvailableTimeResponse> availableTimes = appointmentService.findAvailableTimes(
                1L,
                APPOINTMENT_DATE
        );

        assertEquals(List.of(
                availableTime(10, 0, 10, 30),
                availableTime(10, 30, 11, 0),
                availableTime(11, 0, 11, 30),
                availableTime(11, 30, 12, 0)
        ), availableTimes);
    }

    @Test
    void excludesBookedSlotFromBarberSpecificAvailability() {
        Barber barber = scheduledBarber(LocalTime.of(10, 0), LocalTime.of(12, 0));
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        Appointment bookedAppointment = appointmentAt(LocalTime.of(10, 30));
        when(appointmentRepository.findByBarberIdAndDate(1L, APPOINTMENT_DATE))
                .thenReturn(List.of(bookedAppointment));

        List<AvailableTimeResponse> availableTimes = appointmentService.findAvailableTimes(
                1L,
                APPOINTMENT_DATE
        );

        assertEquals(List.of(
                availableTime(10, 0, 10, 30),
                availableTime(11, 0, 11, 30),
                availableTime(11, 30, 12, 0)
        ), availableTimes);
    }

    @Test
    void usesDifferentBarberScheduleToGenerateAvailableSlots() {
        Barber barber = scheduledBarber(LocalTime.of(9, 30), LocalTime.of(11, 0));
        when(barberRepository.findById(2L)).thenReturn(Optional.of(barber));
        when(appointmentRepository.findByBarberIdAndDate(2L, APPOINTMENT_DATE))
                .thenReturn(List.of());

        List<AvailableTimeResponse> availableTimes = appointmentService.findAvailableTimes(
                2L,
                APPOINTMENT_DATE
        );

        assertEquals(List.of(
                availableTime(9, 30, 10, 0),
                availableTime(10, 0, 10, 30),
                availableTime(10, 30, 11, 0)
        ), availableTimes);
    }

    @Test
    void throwsWhenFindingAvailableTimesForMissingBarber() {
        when(barberRepository.findById(999L)).thenReturn(Optional.empty());

        BarberNotFoundException exception = assertThrows(
                BarberNotFoundException.class,
                () -> appointmentService.findAvailableTimes(999L, APPOINTMENT_DATE)
        );

        assertEquals("Barber not found with id: 999", exception.getMessage());
        verifyNoInteractions(appointmentRepository);
    }

    private void assertInvalidAppointmentTime(LocalTime time) {
        Barber barber = scheduledBarber(LocalTime.of(10, 0), LocalTime.of(18, 0));
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));

        InvalidAppointmentTimeException exception = assertThrows(
                InvalidAppointmentTimeException.class,
                () -> appointmentService.create(requestAt(time))
        );

        assertEquals("Appointment time is outside the allowed schedule", exception.getMessage());
        verify(appointmentRepository, never()).existsByBarberIdAndDateAndTime(
                any(),
                any(),
                any()
        );
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    private AppointmentCreateRequest requestAt(LocalTime time) {
        return new AppointmentCreateRequest(1L, APPOINTMENT_DATE, time, "Reza Karimi");
    }

    private Barber scheduledBarber(LocalTime workStartTime, LocalTime workEndTime) {
        Barber barber = mock(Barber.class);
        when(barber.getWorkStartTime()).thenReturn(workStartTime);
        when(barber.getWorkEndTime()).thenReturn(workEndTime);
        return barber;
    }

    private Appointment appointment(
            Long id,
            Barber barber,
            LocalTime time,
            String clientName
    ) {
        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(id);
        when(appointment.getBarber()).thenReturn(barber);
        when(appointment.getDate()).thenReturn(APPOINTMENT_DATE);
        when(appointment.getTime()).thenReturn(time);
        when(appointment.getClientName()).thenReturn(clientName);
        return appointment;
    }

    private Appointment appointmentAt(LocalTime time) {
        Appointment appointment = mock(Appointment.class);
        when(appointment.getTime()).thenReturn(time);
        return appointment;
    }

    private AvailableTimeResponse availableTime(
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        return new AvailableTimeResponse(
                LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute)
        );
    }
}
