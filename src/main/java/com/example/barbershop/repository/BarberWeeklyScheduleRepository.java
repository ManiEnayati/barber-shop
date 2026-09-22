package com.example.barbershop.repository;

import com.example.barbershop.entity.BarberWeeklySchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface BarberWeeklyScheduleRepository extends JpaRepository<BarberWeeklySchedule, Long> {

    List<BarberWeeklySchedule> findByBarberId(Long barberId);

    Optional<BarberWeeklySchedule> findByBarberIdAndDayOfWeek(Long barberId, DayOfWeek dayOfWeek);

    boolean existsByBarberId(Long barberId);
}
