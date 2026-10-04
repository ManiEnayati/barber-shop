package com.example.barbershop.repository;

import com.example.barbershop.entity.BarberReputation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface BarberReputationRepository
        extends JpaRepository<BarberReputation, Long> {

    Optional<BarberReputation> findByBarberId(Long barberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BarberReputation> findForUpdateByBarberId(Long barberId);
}
