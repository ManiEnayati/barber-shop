package com.example.barbershop.repository;

import com.example.barbershop.entity.Barber;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BarberRepository extends JpaRepository<Barber, Long> {

    boolean existsByUserId(Long userId);
}
