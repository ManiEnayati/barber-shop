package com.example.barbershop.service;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentEvent;
import com.example.barbershop.entity.AppointmentEventType;
import com.example.barbershop.repository.AppointmentEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentEventServiceTests {

    @Mock
    private AppointmentEventRepository appointmentEventRepository;

    @InjectMocks
    private AppointmentEventService appointmentEventService;

    @Test
    void publishSavesAppointmentEvent() {
        Appointment appointment = mock(Appointment.class);

        appointmentEventService.publish(
                appointment,
                AppointmentEventType.APPOINTMENT_CREATED
        );

        ArgumentCaptor<AppointmentEvent> eventCaptor = ArgumentCaptor
                .forClass(AppointmentEvent.class);
        verify(appointmentEventRepository).save(eventCaptor.capture());
        AppointmentEvent event = eventCaptor.getValue();
        assertAll(
                () -> assertSame(appointment, event.getAppointment()),
                () -> assertEquals(
                        AppointmentEventType.APPOINTMENT_CREATED,
                        event.getType()
                ),
                () -> assertNotNull(event.getCreatedAt())
        );
    }
}
