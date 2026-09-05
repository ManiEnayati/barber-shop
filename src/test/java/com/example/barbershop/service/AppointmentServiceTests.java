package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentCreateRequest;
import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.exception.AppointmentSlotAlreadyBookedException;
import com.example.barbershop.exception.BarberNotFoundException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTests {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private BarberRepository barberRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void createsAppointmentForResolvedBarberAndReturnsResponse() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        LocalTime time = LocalTime.of(14, 30);
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                1L,
                date,
                time,
                "Reza Karimi"
        );
        Barber barber = mock(Barber.class);
        when(barber.getId()).thenReturn(1L);
        when(barber.getName()).thenReturn("Ali Rezaei");
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(appointmentRepository.existsByBarberIdAndDateAndTime(1L, date, time))
                .thenReturn(false);

        Appointment savedAppointment = mock(Appointment.class);
        when(savedAppointment.getId()).thenReturn(10L);
        when(savedAppointment.getBarber()).thenReturn(barber);
        when(savedAppointment.getDate()).thenReturn(date);
        when(savedAppointment.getTime()).thenReturn(time);
        when(savedAppointment.getClientName()).thenReturn("Reza Karimi");
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(savedAppointment);

        AppointmentResponse response = appointmentService.create(request);

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(barberRepository).findById(1L);
        verify(appointmentRepository).existsByBarberIdAndDateAndTime(1L, date, time);
        verify(appointmentRepository).save(appointmentCaptor.capture());
        Appointment appointmentToSave = appointmentCaptor.getValue();
        assertAll(
                () -> assertSame(barber, appointmentToSave.getBarber()),
                () -> assertEquals(date, appointmentToSave.getDate()),
                () -> assertEquals(time, appointmentToSave.getTime()),
                () -> assertEquals("Reza Karimi", appointmentToSave.getClientName()),
                () -> assertEquals(
                        new AppointmentResponse(
                                10L,
                                1L,
                                "Ali Rezaei",
                                date,
                                time,
                                "Reza Karimi"
                        ),
                        response
                )
        );
    }

    @Test
    void throwsWhenBarberDoesNotExist() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                999L,
                LocalDate.of(2026, 9, 10),
                LocalTime.of(14, 30),
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
    void rejectsAlreadyBookedSlotWithoutSavingAppointment() {
        LocalDate date = LocalDate.of(2026, 9, 10);
        LocalTime time = LocalTime.of(14, 30);
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                1L,
                date,
                time,
                "Reza Karimi"
        );
        Barber barber = mock(Barber.class);
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(appointmentRepository.existsByBarberIdAndDateAndTime(1L, date, time))
                .thenReturn(true);

        AppointmentSlotAlreadyBookedException exception = assertThrows(
                AppointmentSlotAlreadyBookedException.class,
                () -> appointmentService.create(request)
        );

        assertEquals("Appointment slot is already booked", exception.getMessage());
        verify(barberRepository).findById(1L);
        verify(appointmentRepository).existsByBarberIdAndDateAndTime(1L, date, time);
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }
}
