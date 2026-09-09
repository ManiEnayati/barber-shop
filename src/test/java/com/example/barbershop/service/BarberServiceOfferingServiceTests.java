package com.example.barbershop.service;

import com.example.barbershop.dto.BarberServiceCreateRequest;
import com.example.barbershop.dto.BarberServiceResponse;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.exception.BarberNotFoundException;
import com.example.barbershop.exception.InvalidBarberServiceException;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.BarberServiceOfferingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BarberServiceOfferingServiceTests {

    @Mock
    private BarberServiceOfferingRepository barberServiceOfferingRepository;

    @Mock
    private BarberRepository barberRepository;

    @InjectMocks
    private BarberServiceOfferingService barberServiceOfferingService;

    @Test
    void createsThirtyMinuteServiceAndMapsSavedEntityToResponse() {
        Barber barber = mockBarber(1L, "Ali Rezaei");
        BarberServiceOffering persistedService = mockServiceOffering(
                10L,
                barber,
                "Haircut",
                30,
                400000L
        );
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(barberServiceOfferingRepository.save(any(BarberServiceOffering.class)))
                .thenReturn(persistedService);

        BarberServiceResponse response = barberServiceOfferingService.create(
                request("Haircut", 30, 400000L)
        );

        ArgumentCaptor<BarberServiceOffering> serviceCaptor =
                ArgumentCaptor.forClass(BarberServiceOffering.class);
        verify(barberServiceOfferingRepository).save(serviceCaptor.capture());
        BarberServiceOffering serviceToSave = serviceCaptor.getValue();
        assertAll(
                () -> assertEquals(barber, serviceToSave.getBarber()),
                () -> assertEquals("Haircut", serviceToSave.getName()),
                () -> assertEquals(30, serviceToSave.getDurationMinutes()),
                () -> assertEquals(400000L, serviceToSave.getPrice()),
                () -> assertEquals(
                        new BarberServiceResponse(
                                10L,
                                1L,
                                "Ali Rezaei",
                                "Haircut",
                                30,
                                400000L
                        ),
                        response
                )
        );
    }

    @Test
    void acceptsSixtyMinuteDuration() {
        Barber barber = mockBarber(1L, "Ali Rezaei");
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(barberServiceOfferingRepository.save(any(BarberServiceOffering.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BarberServiceResponse response = barberServiceOfferingService.create(
                request("Hair + Beard", 60, 550000L)
        );

        assertEquals(60, response.durationMinutes());
        verify(barberServiceOfferingRepository).save(any(BarberServiceOffering.class));
    }

    @Test
    void throwsWhenCreatingServiceForMissingBarber() {
        when(barberRepository.findById(999L)).thenReturn(Optional.empty());

        BarberNotFoundException exception = assertThrows(
                BarberNotFoundException.class,
                () -> barberServiceOfferingService.create(
                        new BarberServiceCreateRequest(999L, "Haircut", 30, 400000L)
                )
        );

        assertEquals("Barber not found with id: 999", exception.getMessage());
        verifyNoInteractions(barberServiceOfferingRepository);
    }

    @Test
    void rejectsFifteenMinuteDurationWithoutSaving() {
        assertInvalidService("Haircut", 15, 400000L);
    }

    @Test
    void rejectsFortyFiveMinuteDurationWithoutSaving() {
        assertInvalidService("Haircut", 45, 400000L);
    }

    @Test
    void rejectsZeroDurationWithoutSaving() {
        assertInvalidService("Haircut", 0, 400000L);
    }

    @Test
    void rejectsNegativeDurationWithoutSaving() {
        assertInvalidService("Haircut", -30, 400000L);
    }

    @Test
    void rejectsNegativePriceWithoutSaving() {
        assertInvalidService("Haircut", 30, -1L);
    }

    @Test
    void rejectsNullNameWithoutSaving() {
        assertInvalidService(null, 30, 400000L);
    }

    @Test
    void rejectsBlankNameWithoutSaving() {
        assertInvalidService(" ", 30, 400000L);
    }

    @Test
    void rejectsNullDurationWithoutSaving() {
        assertInvalidService("Haircut", null, 400000L);
    }

    @Test
    void rejectsNullPriceWithoutSaving() {
        assertInvalidService("Haircut", 30, null);
    }

    @Test
    void findsAndMapsServicesForBarber() {
        Barber barber = mockBarber(1L, "Ali Rezaei");
        BarberServiceOffering haircut = mockServiceOffering(
                10L,
                barber,
                "Haircut",
                30,
                400000L
        );
        BarberServiceOffering beard = mockServiceOffering(
                11L,
                barber,
                "Beard",
                30,
                200000L
        );
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(barberServiceOfferingRepository.findByBarberId(1L))
                .thenReturn(List.of(haircut, beard));

        List<BarberServiceResponse> responses =
                barberServiceOfferingService.findByBarber(1L);

        assertEquals(List.of(
                new BarberServiceResponse(
                        10L, 1L, "Ali Rezaei", "Haircut", 30, 400000L
                ),
                new BarberServiceResponse(
                        11L, 1L, "Ali Rezaei", "Beard", 30, 200000L
                )
        ), responses);
        verify(barberServiceOfferingRepository).findByBarberId(1L);
    }

    @Test
    void returnsEmptyListWhenExistingBarberHasNoServices() {
        Barber barber = mock(Barber.class);
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));
        when(barberServiceOfferingRepository.findByBarberId(1L)).thenReturn(List.of());

        List<BarberServiceResponse> responses =
                barberServiceOfferingService.findByBarber(1L);

        assertEquals(List.of(), responses);
        verify(barberServiceOfferingRepository).findByBarberId(1L);
    }

    @Test
    void missingBarberDoesNotQueryServiceRepository() {
        when(barberRepository.findById(999L)).thenReturn(Optional.empty());

        BarberNotFoundException exception = assertThrows(
                BarberNotFoundException.class,
                () -> barberServiceOfferingService.findByBarber(999L)
        );

        assertEquals("Barber not found with id: 999", exception.getMessage());
        verifyNoInteractions(barberServiceOfferingRepository);
    }

    private void assertInvalidService(String name, Integer durationMinutes, Long price) {
        Barber barber = mock(Barber.class);
        when(barberRepository.findById(1L)).thenReturn(Optional.of(barber));

        InvalidBarberServiceException exception = assertThrows(
                InvalidBarberServiceException.class,
                () -> barberServiceOfferingService.create(
                        new BarberServiceCreateRequest(1L, name, durationMinutes, price)
                )
        );

        assertEquals("Barber service is invalid", exception.getMessage());
        verify(barberServiceOfferingRepository, never())
                .save(any(BarberServiceOffering.class));
    }

    private BarberServiceCreateRequest request(
            String name,
            Integer durationMinutes,
            Long price
    ) {
        return new BarberServiceCreateRequest(1L, name, durationMinutes, price);
    }

    private Barber mockBarber(Long id, String name) {
        Barber barber = mock(Barber.class);
        when(barber.getId()).thenReturn(id);
        when(barber.getName()).thenReturn(name);
        return barber;
    }

    private BarberServiceOffering mockServiceOffering(
            Long id,
            Barber barber,
            String name,
            int durationMinutes,
            long price
    ) {
        BarberServiceOffering serviceOffering = mock(BarberServiceOffering.class);
        when(serviceOffering.getId()).thenReturn(id);
        when(serviceOffering.getBarber()).thenReturn(barber);
        when(serviceOffering.getName()).thenReturn(name);
        when(serviceOffering.getDurationMinutes()).thenReturn(durationMinutes);
        when(serviceOffering.getPrice()).thenReturn(price);
        return serviceOffering;
    }
}
