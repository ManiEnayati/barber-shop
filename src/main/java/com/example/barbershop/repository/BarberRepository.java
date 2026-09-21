package com.example.barbershop.repository;

import com.example.barbershop.entity.Barber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BarberRepository extends JpaRepository<Barber, Long> {

    boolean existsByUserId(Long userId);

    Optional<Barber> findByUserId(Long userId);
}
