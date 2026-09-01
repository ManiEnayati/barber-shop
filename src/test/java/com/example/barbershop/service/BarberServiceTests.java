package com.example.barbershop.service;

import com.example.barbershop.dto.BarberCreateRequest;
import com.example.barbershop.dto.BarberResponse;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.repository.BarberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BarberServiceTests {

    @Mock
    private BarberRepository barberRepository;

    @InjectMocks
    private BarberService barberService;

    @Test
    void createsBarberAndReturnsResponse() {
        Barber persistedBarber = mock(Barber.class);
        when(persistedBarber.getId()).thenReturn(1L);
        when(persistedBarber.getName()).thenReturn("Ali Rezaei");
        when(persistedBarber.getPhone()).thenReturn("09120000000");
        when(barberRepository.save(any(Barber.class))).thenReturn(persistedBarber);

        BarberResponse response = barberService.create(
                new BarberCreateRequest("Ali Rezaei", "09120000000")
        );

        ArgumentCaptor<Barber> barberCaptor = ArgumentCaptor.forClass(Barber.class);
        verify(barberRepository).save(barberCaptor.capture());
        assertEquals("Ali Rezaei", barberCaptor.getValue().getName());
        assertEquals("09120000000", barberCaptor.getValue().getPhone());
        assertEquals(new BarberResponse(1L, "Ali Rezaei", "09120000000"), response);
    }

    @Test
    void returnsAllBarbersAsResponses() {
        Barber firstBarber = mockBarber(1L, "Ali Rezaei", "09120000000");
        Barber secondBarber = mockBarber(2L, "Sara Ahmadi", "09121111111");
        when(barberRepository.findAll()).thenReturn(List.of(firstBarber, secondBarber));

        List<BarberResponse> responses = barberService.findAll();

        assertEquals(List.of(
                new BarberResponse(1L, "Ali Rezaei", "09120000000"),
                new BarberResponse(2L, "Sara Ahmadi", "09121111111")
        ), responses);
        verify(barberRepository).findAll();
    }

    private Barber mockBarber(Long id, String name, String phone) {
        Barber barber = mock(Barber.class);
        when(barber.getId()).thenReturn(id);
        when(barber.getName()).thenReturn(name);
        when(barber.getPhone()).thenReturn(phone);
        return barber;
    }
}
