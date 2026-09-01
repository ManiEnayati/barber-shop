package com.example.barbershop.repository;

import com.example.barbershop.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository
        extends JpaRepository<Appointment, Long> {

    List<Appointment> findByBarberId(Long barberId);
}