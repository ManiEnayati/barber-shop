package com.example.barbershop.service;

import com.example.barbershop.dto.BlockedTimeCreateRequest;
import com.example.barbershop.dto.BlockedTimeResponse;
import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.BlockedTime;
import com.example.barbershop.entity.Customer;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.BlockedTimeNotFoundException;
import com.example.barbershop.exception.BlockedTimeOverlapsActiveAppointmentException;
import com.example.barbershop.exception.BlockedTimeOverlapsAnotherBlockedTimeException;
import com.example.barbershop.exception.InvalidBlockedTimeException;
import com.example.barbershop.repository.AppointmentRepository;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BlockedTimeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockedTimeServiceTests {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 12);

    @Mock
    private BlockedTimeRepository blockedTimeRepository;

    @Mock
    private BarberRepository barberRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private BlockedTimeService blockedTimeService;

    @Test
    void createsValidBlockedTime() {
        Barber barber = barber(1L, "Ali Rezaei");
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(blockedTimeRepository.save(any(BlockedTime.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BlockedTimeResponse response = blockedTimeService.create(
                request(LocalTime.of(13, 0), LocalTime.of(14, 0))
        );

        ArgumentCaptor<BlockedTime> captor = ArgumentCaptor.forClass(BlockedTime.class);
        verify(blockedTimeRepository).save(captor.capture());
        assertAll(
                () -> assertSame(barber, captor.getValue().getBarber()),
                () -> assertEquals(DATE, response.date()),
                () -> assertEquals(LocalTime.of(13, 0), response.startTime()),
                () -> assertEquals(LocalTime.of(14, 0), response.endTime()),
                () -> assertEquals("Lunch", response.reason()),
                () -> assertEquals("Ali Rezaei", response.barberName())
        );
    }

    @Test
    void rejectsFifteenMinuteBoundary() {
        stubBarber();

        InvalidBlockedTimeException exception = assertThrows(
                InvalidBlockedTimeException.class,
                () -> blockedTimeService.create(
                        request(LocalTime.of(13, 15), LocalTime.of(14, 0))
                )
        );

        assertEquals("Blocked time is invalid", exception.getMessage());
        verifyNoInteractions(appointmentRepository, blockedTimeRepository);
    }

    @Test
    void rejectsRangeOutsideBarberSchedule() {
        stubBarber();

        assertThrows(
                InvalidBlockedTimeException.class,
                () -> blockedTimeService.create(
                        request(LocalTime.of(9, 30), LocalTime.of(10, 30))
                )
        );

        verifyNoInteractions(appointmentRepository, blockedTimeRepository);
    }

    @Test
    void activeAppointmentPreventsBlocking() {
        Barber barber = stubBarber();
        Appointment appointment = appointment(barber, LocalTime.of(13, 30));
        when(appointmentRepository.findByBarberIdAndDate(1L, DATE))
                .thenReturn(List.of(appointment));

        BlockedTimeOverlapsActiveAppointmentException exception = assertThrows(
                BlockedTimeOverlapsActiveAppointmentException.class,
                () -> blockedTimeService.create(
                        request(LocalTime.of(13, 0), LocalTime.of(14, 0))
                )
        );

        assertEquals(
                "Blocked time overlaps an active appointment",
                exception.getMessage()
        );
        verify(blockedTimeRepository, never()).save(any());
    }

    @Test
    void cancelledAppointmentDoesNotPreventBlocking() {
        Barber barber = stubBarber();
        Appointment appointment = appointment(barber, LocalTime.of(13, 30));
        appointment.cancel();
        when(appointmentRepository.findByBarberIdAndDate(1L, DATE))
                .thenReturn(List.of(appointment));
        when(blockedTimeRepository.save(any(BlockedTime.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        blockedTimeService.create(request(LocalTime.of(13, 0), LocalTime.of(14, 0)));

        verify(blockedTimeRepository).save(any(BlockedTime.class));
    }

    @Test
    void touchingAppointmentBoundaryIsAllowed() {
        Barber barber = stubBarber();
        Appointment appointment = appointment(barber, LocalTime.of(13, 0));
        when(appointmentRepository.findByBarberIdAndDate(1L, DATE))
                .thenReturn(List.of(appointment));
        when(blockedTimeRepository.save(any(BlockedTime.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        blockedTimeService.create(request(LocalTime.of(13, 30), LocalTime.of(14, 0)));

        verify(blockedTimeRepository).save(any(BlockedTime.class));
    }

    @Test
    void overlappingBlockedTimeIsRejected() {
        Barber barber = stubBarber();
        BlockedTime existing = new BlockedTime(
                barber, DATE, LocalTime.of(13, 30), LocalTime.of(14, 30), null
        );
        when(blockedTimeRepository.findByBarberIdAndDate(1L, DATE))
                .thenReturn(List.of(existing));

        BlockedTimeOverlapsAnotherBlockedTimeException exception = assertThrows(
                BlockedTimeOverlapsAnotherBlockedTimeException.class,
                () -> blockedTimeService.create(
                        request(LocalTime.of(13, 0), LocalTime.of(14, 0))
                )
        );

        assertEquals("Blocked time overlaps another blocked time", exception.getMessage());
    }

    @Test
    void listsOnlyRepositoryResultsForRequestedBarberAndDate() {
        Barber barber = stubBarber();
        BlockedTime block = identifiedBlock(
                10L, barber, LocalTime.of(13, 0), LocalTime.of(14, 0)
        );
        when(blockedTimeRepository.findByBarberIdAndDate(1L, DATE))
                .thenReturn(List.of(block));

        List<BlockedTimeResponse> result =
                blockedTimeService.findByBarberAndDate(1L, DATE);

        assertEquals(1, result.size());
        assertEquals(10L, result.getFirst().id());
        verify(blockedTimeRepository).findByBarberIdAndDate(1L, DATE);
    }

    @Test
    void missingBarberIsReported() {
        when(barberRepository.findById(999L)).thenReturn(Optional.empty());

        BarberNotFoundException exception = assertThrows(
                BarberNotFoundException.class,
                () -> blockedTimeService.findByBarberAndDate(999L, DATE)
        );

        assertEquals("Barber not found with id: 999", exception.getMessage());
        verifyNoInteractions(blockedTimeRepository);
    }

    @Test
    void deletesExistingBlockedTime() {
        BlockedTime block = new BlockedTime(
                barber(1L, "Ali Rezaei"),
                DATE,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                null
        );
        when(blockedTimeRepository.findById(10L)).thenReturn(Optional.of(block));

        blockedTimeService.delete(10L);

        verify(blockedTimeRepository).delete(block);
    }

    @Test
    void missingBlockedTimeIsReported() {
        when(blockedTimeRepository.findById(999L)).thenReturn(Optional.empty());

        BlockedTimeNotFoundException exception = assertThrows(
                BlockedTimeNotFoundException.class,
                () -> blockedTimeService.delete(999L)
        );

        assertEquals("Blocked time not found with id: 999", exception.getMessage());
        verify(blockedTimeRepository, never()).delete(any());
    }

    private Barber stubBarber() {
        Barber barber = barber(1L, "Ali Rezaei");
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        return barber;
    }

    private Barber barber(Long id, String name) {
        Barber barber = new Barber(
                name, "09120000000", LocalTime.of(10, 0), LocalTime.of(18, 0)
        );
        setId(barber, id);
        return barber;
    }

    private Appointment appointment(Barber barber, LocalTime time) {
        BarberServiceOffering service = new BarberServiceOffering(
                barber, "Haircut", 30, 400000L
        );
        Customer customer = new Customer("Reza Karimi", "09123334444");
        return new Appointment(barber, service, customer, DATE, time);
    }

    private BlockedTime identifiedBlock(
            Long id,
            Barber barber,
            LocalTime startTime,
            LocalTime endTime
    ) {
        BlockedTime blockedTime = new BlockedTime(
                barber, DATE, startTime, endTime, "Lunch"
        );
        setId(blockedTime, id);
        return blockedTime;
    }

    private BlockedTimeCreateRequest request(LocalTime startTime, LocalTime endTime) {
        return new BlockedTimeCreateRequest(
                1L, DATE, startTime, endTime, "Lunch"
        );
    }

    private void setId(Object target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
