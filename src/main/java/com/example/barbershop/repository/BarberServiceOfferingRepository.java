package com.example.barbershop.repository;

import com.example.barbershop.entity.BarberServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BarberServiceOfferingRepository
        extends JpaRepository<BarberServiceOffering, Long> {

    List<BarberServiceOffering> findByBarberId(Long barberId);
}
