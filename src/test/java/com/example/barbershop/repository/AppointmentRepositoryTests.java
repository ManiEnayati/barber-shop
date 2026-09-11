package com.example.barbershop.repository;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class AppointmentRepositoryTests {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private BarberRepository barberRepository;

    @Autowired
    private BarberServiceOfferingRepository barberServiceOfferingRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAppointmentWithBarberAndServiceForeignKeys() {
        Barber barber = saveBarber(
                "Ali Rezaei", "09120000000", LocalTime.of(10, 0), LocalTime.of(18, 0)
        );
        BarberServiceOffering service = saveService(
                barber, "Hair + Beard", 60, 550000L
        );
        Appointment appointment = appointmentRepository.saveAndFlush(new Appointment(
                barber,
                service,
                LocalDate.of(2026, 9, 10),
                LocalTime.of(10, 30),
                "Reza Karimi"
        ));
        Long appointmentId = appointment.getId();
        entityManager.clear();

        Appointment persistedAppointment =
                appointmentRepository.findById(appointmentId).orElseThrow();
        Object[] foreignKeys = (Object[]) entityManager.createNativeQuery(
                        "select barber_id, barber_service_id, status "
                                + "from appointments where id = :appointmentId"
                )
                .setParameter("appointmentId", appointmentId)
                .getSingleResult();

        assertAll(
                () -> assertNotNull(persistedAppointment.getId()),
                () -> assertEquals(barber.getId(), persistedAppointment.getBarber().getId()),
                () -> assertEquals(
                        service.getId(),
                        persistedAppointment.getServiceOffering().getId()
                ),
                () -> assertEquals(
                        "Hair + Beard",
                        persistedAppointment.getServiceOffering().getName()
                ),
                () -> assertEquals(
                        barber.getId().longValue(),
                        ((Number) foreignKeys[0]).longValue()
                ),
                () -> assertEquals(
                        service.getId().longValue(),
                        ((Number) foreignKeys[1]).longValue()
                ),
                () -> assertEquals(
                        AppointmentStatus.BOOKED,
                        persistedAppointment.getStatus()
                ),
                () -> assertEquals("BOOKED", foreignKeys[2])
        );
    }

    @Test
    void findsOnlyAppointmentsBelongingToRequestedBarber() {
        Barber requestedBarber = saveBarber(
                "Ali Rezaei", "09120000000", LocalTime.of(10, 0), LocalTime.of(18, 0)
        );
        Barber otherBarber = saveBarber(
                "Sara Ahmadi", "09121111111", LocalTime.of(9, 30), LocalTime.of(17, 0)
        );
        BarberServiceOffering requestedService = saveService(
                requestedBarber, "Haircut", 30, 400000L
        );
        BarberServiceOffering otherService = saveService(
                otherBarber, "Beard", 30, 200000L
        );

        appointmentRepository.saveAllAndFlush(List.of(
                new Appointment(
                        requestedBarber,
                        requestedService,
                        LocalDate.of(2026, 9, 10),
                        LocalTime.of(10, 30),
                        "Reza Karimi"
                ),
                new Appointment(
                        requestedBarber,
                        requestedService,
                        LocalDate.of(2026, 9, 11),
                        LocalTime.of(14, 0),
                        "Mina Jafari"
                ),
                new Appointment(
                        otherBarber,
                        otherService,
                        LocalDate.of(2026, 9, 10),
                        LocalTime.of(11, 0),
                        "Nima Hosseini"
                )
        ));
        entityManager.clear();

        List<Appointment> appointments =
                appointmentRepository.findByBarberId(requestedBarber.getId());

        assertAll(
                () -> assertEquals(2, appointments.size()),
                () -> assertEquals(
                        Set.of("Reza Karimi", "Mina Jafari"),
                        appointments.stream()
                                .map(Appointment::getClientName)
                                .collect(Collectors.toSet())
                ),
                () -> assertTrue(appointments.stream().allMatch(
                        item -> requestedBarber.getId().equals(item.getBarber().getId())
                ))
        );
    }

    @Test
    void findsOnlyAppointmentsForRequestedBarberAndDate() {
        Barber requestedBarber = saveBarber(
                "Ali Rezaei", "09120000000", LocalTime.of(10, 0), LocalTime.of(18, 0)
        );
        Barber otherBarber = saveBarber(
                "Sara Ahmadi", "09121111111", LocalTime.of(9, 30), LocalTime.of(17, 0)
        );
        BarberServiceOffering requestedService = saveService(
                requestedBarber, "Haircut", 30, 400000L
        );
        BarberServiceOffering otherService = saveService(
                otherBarber, "Beard", 30, 200000L
        );
        LocalDate requestedDate = LocalDate.of(2026, 9, 10);

        appointmentRepository.saveAllAndFlush(List.of(
                new Appointment(
                        requestedBarber,
                        requestedService,
                        requestedDate,
                        LocalTime.of(10, 30),
                        "Reza Karimi"
                ),
                new Appointment(
                        requestedBarber,
                        requestedService,
                        requestedDate,
                        LocalTime.of(14, 0),
                        "Mina Jafari"
                ),
                new Appointment(
                        requestedBarber,
                        requestedService,
                        LocalDate.of(2026, 9, 11),
                        LocalTime.of(11, 0),
                        "Nima Hosseini"
                ),
                new Appointment(
                        otherBarber,
                        otherService,
                        requestedDate,
                        LocalTime.of(15, 0),
                        "Sara Mohammadi"
                )
        ));
        entityManager.clear();

        List<Appointment> appointments = appointmentRepository.findByBarberIdAndDate(
                requestedBarber.getId(),
                requestedDate
        );

        assertAll(
                () -> assertEquals(2, appointments.size()),
                () -> assertEquals(
                        Set.of("Reza Karimi", "Mina Jafari"),
                        appointments.stream()
                                .map(Appointment::getClientName)
                                .collect(Collectors.toSet())
                ),
                () -> assertTrue(appointments.stream().allMatch(
                        item -> requestedBarber.getId().equals(item.getBarber().getId())
                )),
                () -> assertTrue(appointments.stream().allMatch(
                        item -> requestedDate.equals(item.getDate())
                ))
        );
    }

    private Barber saveBarber(
            String name,
            String phone,
            LocalTime workStartTime,
            LocalTime workEndTime
    ) {
        return barberRepository.save(new Barber(
                name,
                phone,
                workStartTime,
                workEndTime
        ));
    }

    private BarberServiceOffering saveService(
            Barber barber,
            String name,
            int durationMinutes,
            long price
    ) {
        return barberServiceOfferingRepository.save(new BarberServiceOffering(
                barber,
                name,
                durationMinutes,
                price
        ));
    }
}
