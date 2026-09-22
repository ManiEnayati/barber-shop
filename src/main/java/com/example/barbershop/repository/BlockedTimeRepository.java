package com.example.barbershop.repository;

import com.example.barbershop.entity.BlockedTime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BlockedTimeRepository extends JpaRepository<BlockedTime, Long> {

    List<BlockedTime> findByBarberIdAndDate(Long barberId, LocalDate date);

    List<BlockedTime> findByBarberIdAndDateBetweenOrderByDateAscStartTimeAsc(
            Long barberId, LocalDate startDate, LocalDate endDate);
}
