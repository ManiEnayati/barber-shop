package com.example.barbershop.repository;

import com.example.barbershop.entity.AppointmentConfirmation;
import com.example.barbershop.entity.AppointmentStatus;
import com.example.barbershop.entity.BookingConfirmationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentConfirmationRepository
        extends JpaRepository<AppointmentConfirmation, Long> {

    Optional<AppointmentConfirmation> findByAppointmentId(Long appointmentId);

    @Query("""
            select confirmation from AppointmentConfirmation confirmation
            where confirmation.appointment.confirmationStatus = :confirmationStatus
              and confirmation.appointment.status = :appointmentStatus
              and confirmation.expiresAt <= :now
            """)
    List<AppointmentConfirmation> findExpiredPending(
            @Param("confirmationStatus") BookingConfirmationStatus confirmationStatus,
            @Param("appointmentStatus") AppointmentStatus appointmentStatus,
            @Param("now") LocalDateTime now
    );
}
