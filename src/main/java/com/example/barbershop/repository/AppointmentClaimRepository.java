package com.example.barbershop.repository;

import com.example.barbershop.entity.AppointmentClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentClaimRepository
        extends JpaRepository<AppointmentClaim, Long> {

    boolean existsByAppointmentIdAndUserId(Long appointmentId, Long userId);

    List<AppointmentClaim> findByUserId(Long userId);
}
