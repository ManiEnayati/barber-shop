package com.example.barbershop.service;

import com.example.barbershop.dto.BarberCreateRequest;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.exception.InvalidBarberScheduleException;
import com.example.barbershop.repository.BarberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BarberServiceTests {

    @Mock
    private BarberRepository barberRepository;

    private BarberService barberService;

    @BeforeEach
    void setUp() {
        barberService = new BarberService(
                barberRepository,
                new BarberScheduleValidator()
        );
    }

    @Test
    void createsBarberWithTenToEighteenScheduleAndReturnsWorkingHours() {
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(18, 0);
        Barber persistedBarber = mockBarber(
                1L,
                "Ali Rezaei",
                "09120000000",
                startTime,
                endTime
        );
        when(barberRepository.save(any(Barber.class))).thenReturn(persistedBarber);

        BarberResponse response = barberService.create(
                new BarberCreateRequest(
                        "Ali Rezaei",
                        "09120000000",
                        startTime,
                        endTime
                )
        );

        ArgumentCaptor<Barber> barberCaptor = ArgumentCaptor.forClass(Barber.class);
        verify(barberRepository).save(barberCaptor.capture());
        Barber barberToSave = barberCaptor.getValue();
        assertAll(
                () -> assertEquals("Ali Rezaei", barberToSave.getName()),
                () -> assertEquals("09120000000", barberToSave.getPhone()),
                () -> assertEquals(startTime, barberToSave.getWorkStartTime()),
                () -> assertEquals(endTime, barberToSave.getWorkEndTime()),
                () -> assertEquals(
                        new BarberResponse(
                                1L,
                                "Ali Rezaei",
                                "09120000000",
                                startTime,
                                endTime
                        ),
                        response
                )
        );
    }

    @Test
    void acceptsScheduleStartingAndEndingOnHalfHourBoundaries() {
        LocalTime startTime = LocalTime.of(9, 30);
        LocalTime endTime = LocalTime.of(17, 0);
        Barber persistedBarber = mockBarber(
                2L,
                "Sara Ahmadi",
                "09121111111",
                startTime,
                endTime
        );
        when(barberRepository.save(any(Barber.class))).thenReturn(persistedBarber);

        BarberResponse response = barberService.create(
                new BarberCreateRequest(
                        "Sara Ahmadi",
                        "09121111111",
                        startTime,
                        endTime
                )
        );

        assertEquals(startTime, response.workStartTime());
        assertEquals(endTime, response.workEndTime());
        verify(barberRepository).save(any(Barber.class));
    }

    @Test
    void returnsAllBarbersWithTheirWorkingHours() {
        Barber firstBarber = mockBarber(
                1L,
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        );
        Barber secondBarber = mockBarber(
                2L,
                "Sara Ahmadi",
                "09121111111",
                LocalTime.of(9, 30),
                LocalTime.of(17, 0)
        );
        when(barberRepository.findAll()).thenReturn(List.of(firstBarber, secondBarber));

        List<BarberResponse> responses = barberService.findAll();

        assertEquals(List.of(
                new BarberResponse(
                        1L,
                        "Ali Rezaei",
                        "09120000000",
                        LocalTime.of(10, 0),
                        LocalTime.of(18, 0)
                ),
                new BarberResponse(
                        2L,
                        "Sara Ahmadi",
                        "09121111111",
                        LocalTime.of(9, 30),
                        LocalTime.of(17, 0)
                )
        ), responses);
        verify(barberRepository).findAll();
    }

    @Test
    void rejectsStartTimeOutsideThirtyMinuteBoundaryWithoutSaving() {
        assertInvalidSchedule(LocalTime.of(10, 15), LocalTime.of(18, 0));
    }

    @Test
    void rejectsEndTimeOutsideThirtyMinuteBoundaryWithoutSaving() {
        assertInvalidSchedule(LocalTime.of(10, 0), LocalTime.of(17, 45));
    }

    @Test
    void rejectsScheduleWithEqualStartAndEndWithoutSaving() {
        assertInvalidSchedule(LocalTime.of(10, 0), LocalTime.of(10, 0));
    }

    @Test
    void rejectsScheduleWithStartAfterEndWithoutSaving() {
        assertInvalidSchedule(LocalTime.of(18, 0), LocalTime.of(10, 0));
    }

    private void assertInvalidSchedule(LocalTime startTime, LocalTime endTime) {
        InvalidBarberScheduleException exception = assertThrows(
                InvalidBarberScheduleException.class,
                () -> barberService.create(new BarberCreateRequest(
                        "Ali Rezaei",
                        "09120000000",
                        startTime,
                        endTime
                ))
        );

        assertEquals("Barber work schedule is invalid", exception.getMessage());
        verify(barberRepository, never()).save(any(Barber.class));
    }

    private Barber mockBarber(
            Long id,
            String name,
            String phone,
            LocalTime workStartTime,
            LocalTime workEndTime
    ) {
        Barber barber = mock(Barber.class);
        when(barber.getId()).thenReturn(id);
        when(barber.getName()).thenReturn(name);
        when(barber.getPhone()).thenReturn(phone);
        when(barber.getWorkStartTime()).thenReturn(workStartTime);
        when(barber.getWorkEndTime()).thenReturn(workEndTime);
        return barber;
    }
}
