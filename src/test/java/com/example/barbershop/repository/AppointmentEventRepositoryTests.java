package com.example.barbershop.repository;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentEvent;
import com.example.barbershop.entity.AppointmentEventType;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.Customer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class AppointmentEventRepositoryTests {

    @Autowired
    private AppointmentEventRepository appointmentEventRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private BarberRepository barberRepository;

    @Autowired
    private BarberServiceOfferingRepository barberServiceOfferingRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void storesAndFindsEventsForRequestedAppointment() {
        Barber barber = barberRepository.save(new Barber(
                "Ali Rezaei",
                "09120000000",
                LocalTime.of(10, 0),
                LocalTime.of(18, 0)
        ));
        BarberServiceOffering service = barberServiceOfferingRepository.save(
                new BarberServiceOffering(barber, "Haircut", 30, 400000L)
        );
        Customer customer = customerRepository.save(
                new Customer("Reza Karimi", "09123334444")
        );
        Appointment requestedAppointment = appointmentRepository.save(new Appointment(
                barber,
                service,
                customer,
                LocalDate.of(2026, 9, 16),
                LocalTime.of(10, 0)
        ));
        Appointment otherAppointment = appointmentRepository.save(new Appointment(
                barber,
                service,
                customer,
                LocalDate.of(2026, 9, 17),
                LocalTime.of(11, 0)
        ));
        appointmentEventRepository.saveAllAndFlush(List.of(
                new AppointmentEvent(
                        requestedAppointment,
                        AppointmentEventType.APPOINTMENT_CREATED
                ),
                new AppointmentEvent(
                        requestedAppointment,
                        AppointmentEventType.APPOINTMENT_RESCHEDULED
                ),
                new AppointmentEvent(
                        otherAppointment,
                        AppointmentEventType.APPOINTMENT_CREATED
                )
        ));
        entityManager.clear();

        List<AppointmentEvent> events = appointmentEventRepository
                .findByAppointmentId(requestedAppointment.getId());

        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(event ->
                requestedAppointment.getId().equals(event.getAppointment().getId())
        ));
        assertEquals(
                Set.of(
                        AppointmentEventType.APPOINTMENT_CREATED,
                        AppointmentEventType.APPOINTMENT_RESCHEDULED
                ),
                events.stream()
                        .map(AppointmentEvent::getType)
                        .collect(Collectors.toSet())
        );
        assertTrue(events.stream().allMatch(event -> event.getId() != null));
        assertTrue(events.stream().allMatch(event -> event.getCreatedAt() != null));
        assertNotNull(events.getFirst().getAppointment());
    }
}
