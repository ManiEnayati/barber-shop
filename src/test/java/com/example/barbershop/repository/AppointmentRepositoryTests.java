package com.example.barbershop.repository;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.Barber;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class AppointmentRepositoryTests {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private BarberRepository barberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAppointmentWithCorrectBarberForeignKey() {
        Barber barber = barberRepository.saveAndFlush(
                new Barber(
                        "Ali Rezaei",
                        "09120000000",
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)
                )
        );
        Appointment appointment = appointmentRepository.saveAndFlush(
                new Appointment(
                        barber,
                        LocalDate.of(2026, 9, 10),
                        LocalTime.of(10, 30),
                        "Reza Karimi"
                )
        );

        entityManager.clear();

        Appointment persistedAppointment = appointmentRepository.findById(appointment.getId())
                .orElseThrow();
        Number storedBarberId = (Number) entityManager.createNativeQuery(
                        "select barber_id from appointments where id = :appointmentId"
                )
                .setParameter("appointmentId", appointment.getId())
                .getSingleResult();

        assertAll(
                () -> assertNotNull(persistedAppointment.getId()),
                () -> assertEquals(barber.getId(), persistedAppointment.getBarber().getId()),
                () -> assertEquals(barber.getName(), persistedAppointment.getBarber().getName()),
                () -> assertEquals(barber.getId().longValue(), storedBarberId.longValue())
        );
    }

    @Test
    void findsOnlyAppointmentsBelongingToRequestedBarber() {
        Barber requestedBarber = barberRepository.save(
                new Barber(
                        "Ali Rezaei",
                        "09120000000",
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)
                )
        );
        Barber otherBarber = barberRepository.save(
                new Barber(
                        "Sara Ahmadi",
                        "09121111111",
                        LocalTime.of(9, 30),
                        LocalTime.of(17, 0)
                )
        );

        appointmentRepository.saveAllAndFlush(List.of(
                new Appointment(
                        requestedBarber,
                        LocalDate.of(2026, 9, 10),
                        LocalTime.of(10, 30),
                        "Reza Karimi"
                ),
                new Appointment(
                        requestedBarber,
                        LocalDate.of(2026, 9, 11),
                        LocalTime.of(14, 0),
                        "Mina Jafari"
                ),
                new Appointment(
                        otherBarber,
                        LocalDate.of(2026, 9, 10),
                        LocalTime.of(11, 0),
                        "Nima Hosseini"
                )
        ));
        entityManager.clear();

        List<Appointment> appointments = appointmentRepository.findByBarberId(
                requestedBarber.getId()
        );
        Set<String> clientNames = appointments.stream()
                .map(Appointment::getClientName)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(2, appointments.size()),
                () -> assertEquals(Set.of("Reza Karimi", "Mina Jafari"), clientNames),
                () -> assertEquals(
                        Set.of(requestedBarber.getId()),
                        appointments.stream()
                                .map(appointment -> appointment.getBarber().getId())
                                .collect(Collectors.toSet())
                )
        );
    }

    @Test
    void findsOnlyAppointmentsForRequestedBarberAndDate() {
        Barber requestedBarber = barberRepository.save(
                new Barber(
                        "Ali Rezaei",
                        "09120000000",
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)
                )
        );
        Barber otherBarber = barberRepository.save(
                new Barber(
                        "Sara Ahmadi",
                        "09121111111",
                        LocalTime.of(9, 30),
                        LocalTime.of(17, 0)
                )
        );
        LocalDate requestedDate = LocalDate.of(2026, 9, 10);

        appointmentRepository.saveAllAndFlush(List.of(
                new Appointment(
                        requestedBarber,
                        requestedDate,
                        LocalTime.of(10, 30),
                        "Reza Karimi"
                ),
                new Appointment(
                        requestedBarber,
                        requestedDate,
                        LocalTime.of(14, 0),
                        "Mina Jafari"
                ),
                new Appointment(
                        requestedBarber,
                        LocalDate.of(2026, 9, 11),
                        LocalTime.of(11, 0),
                        "Nima Hosseini"
                ),
                new Appointment(
                        otherBarber,
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
                        appointment -> requestedBarber.getId().equals(
                                appointment.getBarber().getId()
                        )
                )),
                () -> assertTrue(appointments.stream().allMatch(
                        appointment -> requestedDate.equals(appointment.getDate())
                ))
        );
    }

    @Test
    void detectsOnlyExactBarberDateAndTimeCombination() {
        Barber bookedBarber = barberRepository.save(
                new Barber(
                        "Ali Rezaei",
                        "09120000000",
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)
                )
        );
        Barber otherBarber = barberRepository.save(
                new Barber(
                        "Sara Ahmadi",
                        "09121111111",
                        LocalTime.of(9, 30),
                        LocalTime.of(17, 0)
                )
        );
        LocalDate bookedDate = LocalDate.of(2026, 9, 10);
        LocalTime bookedTime = LocalTime.of(14, 30);
        appointmentRepository.saveAndFlush(
                new Appointment(bookedBarber, bookedDate, bookedTime, "Reza Karimi")
        );
        entityManager.clear();

        assertAll(
                () -> assertTrue(
                        appointmentRepository.existsByBarberIdAndDateAndTime(
                                bookedBarber.getId(), bookedDate, bookedTime
                        )
                ),
                () -> assertFalse(
                        appointmentRepository.existsByBarberIdAndDateAndTime(
                                bookedBarber.getId(), bookedDate, LocalTime.of(15, 0)
                        )
                ),
                () -> assertFalse(
                        appointmentRepository.existsByBarberIdAndDateAndTime(
                                bookedBarber.getId(), LocalDate.of(2026, 9, 11), bookedTime
                        )
                ),
                () -> assertFalse(
                        appointmentRepository.existsByBarberIdAndDateAndTime(
                                otherBarber.getId(), bookedDate, bookedTime
                        )
                )
        );
    }
}
