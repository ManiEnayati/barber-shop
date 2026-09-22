package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.BarberCalendarSlotResponse;
import com.example.barbershop.dto.BarberCalendarSlotStatus;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.User;
import com.example.barbershop.entity.UserRole;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BarberDashboardServiceTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 25);

    @Mock private UserRepository userRepository;
    @Mock private BarberRepository barberRepository;
    @Mock private AppointmentService appointmentService;
    @Mock private BarberScheduleService scheduleService;

    @InjectMocks private BarberDashboardService barberDashboardService;

    @Test
    void returnsProfileLinkedToAuthenticatedBarberUser() {
        User user = barberUser();
        Barber barber = barber();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(barberRepository.findByUserId(7L)).thenReturn(Optional.of(barber));

        BarberResponse response = barberDashboardService.getCurrentBarber(7L);

        assertAll(
                () -> assertEquals(11L, response.id()),
                () -> assertEquals("Ali Rezaei", response.name()),
                () -> assertEquals("+989121111111", response.phone()),
                () -> assertEquals(LocalTime.of(10, 0), response.workStartTime()),
                () -> assertEquals(LocalTime.of(18, 0), response.workEndTime())
        );
    }

    @Test
    void rejectsUserWhoseDatabaseRoleIsNotBarber() {
        User user = mock(User.class);
        when(user.isPhoneVerified()).thenReturn(true);
        when(user.getRoles()).thenReturn(Set.of(UserRole.CUSTOMER));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> barberDashboardService.getCurrentBarber(7L)
        );

        assertEquals("Barber role is required", exception.getMessage());
        verifyNoInteractions(barberRepository, appointmentService);
    }

    @Test
    void rejectsBarberRoleWithoutLinkedProfile() {
        User user = barberUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(barberRepository.findByUserId(7L)).thenReturn(Optional.empty());

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> barberDashboardService.getCurrentBarber(7L)
        );

        assertEquals("Linked barber profile is required", exception.getMessage());
        verifyNoInteractions(appointmentService);
    }

    @Test
    void calendarUsesAuthenticatedUsersLinkedBarber() {
        User user = barberUser();
        Barber barber = barber();
        List<BarberCalendarSlotResponse> slots = List.of(
                new BarberCalendarSlotResponse(
                        LocalTime.of(10, 0),
                        BarberCalendarSlotStatus.AVAILABLE,
                        null
                )
        );
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(barberRepository.findByUserId(7L)).thenReturn(Optional.of(barber));
        when(appointmentService.findCalendarSlots(barber, DATE)).thenReturn(slots);
        when(scheduleService.workingHours(barber, DATE)).thenReturn(Optional.of(
                new BarberScheduleService.WorkingHours(LocalTime.of(10, 0),
                        LocalTime.of(18, 0))));

        var response = barberDashboardService.getCalendar(7L, DATE);

        assertAll(
                () -> assertEquals(DATE, response.date()),
                () -> assertEquals(LocalTime.of(10, 0), response.workingStart()),
                () -> assertEquals(LocalTime.of(18, 0), response.workingEnd()),
                () -> assertEquals(slots, response.slots())
        );
        verify(appointmentService).findCalendarSlots(barber, DATE);
    }

    @Test
    void appointmentFiltersUseAuthenticatedUsersLinkedBarberId() {
        User user = barberUser();
        Barber barber = barber();
        List<AppointmentResponse> appointments = List.of();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(barberRepository.findByUserId(7L)).thenReturn(Optional.of(barber));
        when(appointmentService.findByBarber(
                11L, DATE, AppointmentStatus.BOOKED
        )).thenReturn(appointments);

        List<AppointmentResponse> response = barberDashboardService.getAppointments(
                7L, DATE, AppointmentStatus.BOOKED
        );

        assertEquals(appointments, response);
        verify(appointmentService).findByBarber(
                11L, DATE, AppointmentStatus.BOOKED
        );
    }

    private User barberUser() {
        User user = mock(User.class);
        when(user.isPhoneVerified()).thenReturn(true);
        when(user.getRoles()).thenReturn(Set.of(UserRole.CUSTOMER, UserRole.BARBER));
        return user;
    }

    private Barber barber() {
        Barber barber = mock(Barber.class);
        lenient().when(barber.getId()).thenReturn(11L);
        lenient().when(barber.getName()).thenReturn("Ali Rezaei");
        lenient().when(barber.getPhone()).thenReturn("+989121111111");
        lenient().when(barber.getWorkStartTime()).thenReturn(LocalTime.of(10, 0));
        lenient().when(barber.getWorkEndTime()).thenReturn(LocalTime.of(18, 0));
        return barber;
    }
}
