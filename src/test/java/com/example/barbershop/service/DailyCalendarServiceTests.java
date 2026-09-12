package com.example.barbershop.service;

import com.example.barbershop.dto.AppointmentResponse;
import com.example.barbershop.dto.BlockedTimeResponse;
import com.example.barbershop.dto.DailyCalendarResponse;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.repository.BarberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyCalendarServiceTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 13);

    @Mock
    private BarberRepository barberRepository;

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private BlockedTimeService blockedTimeService;

    @InjectMocks
    private DailyCalendarService dailyCalendarService;

    @Test
    void returnsBarberMetadataAndEmptyDay() {
        Barber barber = barber();
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(appointmentService.findByBarberAndDate(1L, DATE)).thenReturn(List.of());
        when(blockedTimeService.findByBarberAndDate(1L, DATE)).thenReturn(List.of());

        DailyCalendarResponse response =
                dailyCalendarService.getDailyCalendar(1L, DATE);

        assertAll(
                () -> assertEquals(1L, response.barberId()),
                () -> assertEquals("Ali Rezaei", response.barberName()),
                () -> assertEquals(DATE, response.date()),
                () -> assertEquals(LocalTime.of(10, 0), response.workStartTime()),
                () -> assertEquals(LocalTime.of(18, 0), response.workEndTime()),
                () -> assertEquals(List.of(), response.appointments()),
                () -> assertEquals(List.of(), response.blockedTimes())
        );
    }

    @Test
    void sortsAppointmentsAndBlocksWithoutFilteringHistoricalStatuses() {
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber()));
        AppointmentResponse noShow = appointment(
                3L, LocalTime.of(15, 0), AppointmentStatus.NO_SHOW
        );
        AppointmentResponse cancelled = appointment(
                1L, LocalTime.of(11, 0), AppointmentStatus.CANCELLED
        );
        AppointmentResponse completed = appointment(
                2L, LocalTime.of(13, 0), AppointmentStatus.COMPLETED
        );
        when(appointmentService.findByBarberAndDate(1L, DATE))
                .thenReturn(List.of(noShow, cancelled, completed));
        BlockedTimeResponse laterBlock = block(2L, LocalTime.of(16, 0));
        BlockedTimeResponse earlierBlock = block(1L, LocalTime.of(12, 0));
        when(blockedTimeService.findByBarberAndDate(1L, DATE))
                .thenReturn(List.of(laterBlock, earlierBlock));

        DailyCalendarResponse response =
                dailyCalendarService.getDailyCalendar(1L, DATE);

        assertAll(
                () -> assertEquals(
                        List.of(cancelled, completed, noShow),
                        response.appointments()
                ),
                () -> assertEquals(
                        List.of(earlierBlock, laterBlock),
                        response.blockedTimes()
                )
        );
    }

    @Test
    void missingBarberStopsCalendarLookup() {
        when(barberRepository.findById(999L)).thenReturn(Optional.empty());

        BarberNotFoundException exception = assertThrows(
                BarberNotFoundException.class,
                () -> dailyCalendarService.getDailyCalendar(999L, DATE)
        );

        assertEquals("Barber not found with id: 999", exception.getMessage());
        verifyNoInteractions(appointmentService, blockedTimeService);
    }

    private Barber barber() {
        Barber barber = new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        );
        try {
            Field id = Barber.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(barber, 1L);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return barber;
    }

    private AppointmentResponse appointment(
            Long id,
            LocalTime time,
            AppointmentStatus status
    ) {
        return new AppointmentResponse(
                id,
                1L,
                "Ali Rezaei",
                10L,
                "Haircut",
                30,
                DATE,
                time,
                time.plusMinutes(30),
                100L,
                "Reza Karimi",
                "09123334444",
                status
        );
    }

    private BlockedTimeResponse block(Long id, LocalTime startTime) {
        return new BlockedTimeResponse(
                id,
                1L,
                "Ali Rezaei",
                DATE,
                startTime,
                startTime.plusMinutes(30),
                "Break"
        );
    }
}
