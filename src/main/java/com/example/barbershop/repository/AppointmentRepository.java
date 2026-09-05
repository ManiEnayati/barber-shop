package com.example.barbershop.repository;

import com.example.barbershop.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface AppointmentRepository
        extends JpaRepository<Appointment, Long> {

    List<Appointment> findByBarberId(Long barberId);

    List<Appointment> findByBarberIdAndDate(
            Long barberId,
            LocalDate date
    );

    boolean existsByBarberIdAndDateAndTime(
            Long barberId,
            LocalDate date,
            LocalTime time
    );
}