package com.example.barbershop.repository;

import com.example.barbershop.entity.Appointment;
import com.example.barbershop.entity.AppointmentClaim;
import com.example.barbershop.entity.AppointmentClaimStatus;
import com.example.barbershop.entity.Barber;
import com.example.barbershop.entity.BarberServiceOffering;
import com.example.barbershop.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class AppointmentClaimRepositoryTests {

    @Autowired private AppointmentClaimRepository appointmentClaimRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BarberRepository barberRepository;
    @Autowired private BarberServiceOfferingRepository serviceRepository;

    @Test
    void storesDecisionAndFindsItByUser() {
        User user = saveUser();
        Appointment appointment = saveAppointment();

        AppointmentClaim claim = appointmentClaimRepository.saveAndFlush(
                new AppointmentClaim(
                        appointment,
                        user,
                        AppointmentClaimStatus.REJECTED
                )
        );

        assertNotNull(claim.getId());
        assertNotNull(claim.getDecidedAt());
        assertEquals(AppointmentClaimStatus.REJECTED, claim.getStatus());
        assertEquals(
                claim.getId(),
                appointmentClaimRepository.findByUserId(user.getId())
                        .getFirst()
                        .getId()
        );
        assertTrue(appointmentClaimRepository
                .existsByAppointmentIdAndUserId(appointment.getId(), user.getId()));
    }

    @Test
    void sameUserCannotDecideSameAppointmentTwice() {
        User user = saveUser();
        Appointment appointment = saveAppointment();
        appointmentClaimRepository.saveAndFlush(new AppointmentClaim(
                appointment,
                user,
                AppointmentClaimStatus.REJECTED
        ));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> appointmentClaimRepository.saveAndFlush(new AppointmentClaim(
                        appointment,
                        user,
                        AppointmentClaimStatus.CONFIRMED
                ))
        );
    }

    private User saveUser() {
        User user = new User("+989121234567");
        user.verifyPhone();
        return userRepository.save(user);
    }

    private Appointment saveAppointment() {
        Barber barber = barberRepository.save(new Barber(
                "Ali", "09120000000",
                LocalTime.of(10, 0), LocalTime.of(18, 0)
        ));
        BarberServiceOffering service = serviceRepository.save(
                new BarberServiceOffering(barber, "Haircut", 30, 400000L)
        );
        return appointmentRepository.save(new Appointment(
                barber,
                service,
                "Guest",
                "+989121234567",
                LocalDate.of(2026, 9, 25),
                LocalTime.of(10, 0)
        ));
    }
}
