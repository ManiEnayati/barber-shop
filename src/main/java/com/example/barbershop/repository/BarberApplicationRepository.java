package com.example.barbershop.repository;

import com.example.barbershop.entity.BarberApplication;
import com.example.barbershop.entity.BarberApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BarberApplicationRepository
        extends JpaRepository<BarberApplication, Long> {

    boolean existsByUserIdAndStatus(
            Long userId,
            BarberApplicationStatus status
    );

    List<BarberApplication> findByUserIdOrderBySubmittedAtDescIdDesc(Long userId);

    List<BarberApplication> findAllByOrderBySubmittedAtDescIdDesc();

    List<BarberApplication> findByStatusOrderBySubmittedAtDescIdDesc(
            BarberApplicationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from BarberApplication application "
            + "join fetch application.user where application.id = :id")
    Optional<BarberApplication> findByIdForReview(@Param("id") Long id);
}
